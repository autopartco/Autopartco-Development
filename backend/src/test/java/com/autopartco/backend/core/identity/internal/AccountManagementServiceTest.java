package com.autopartco.backend.core.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.identity.internal.social.AccountIdentityRepository;
import com.autopartco.backend.core.notification.NotificationKind;
import com.autopartco.backend.core.notification.NotificationService;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.TooManyRequestsException;

/** Administración de la cuenta: contraseña, correo con código, desvincular, eliminación. */
class AccountManagementServiceTest {
    private static final String EMAIL = "ana@correo.co";

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AccountTokenRepository tokens = mock(AccountTokenRepository.class);
    private final EmailVerificationTokenService tokenService = mock(EmailVerificationTokenService.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AccountSessionService sessions = mock(AccountSessionService.class);
    private final AccountIdentityRepository identities = mock(AccountIdentityRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AuditService audit = mock(AuditService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AccountManagementService service = new AccountManagementService(accounts, tokens, tokenService,
            new TokenMapper(), encoder, sessions, identities, events, audit, notifications, jdbc);
    private Account account;

    @BeforeEach
    void setUp() {
        Person person = Person.builder().name("Ana").surname("Pérez").build();
        person.setId(UUID.randomUUID());
        account = Account.builder().email(EMAIL).password("hash").person(person).configuration(Map.of()).build();
        account.setId(UUID.randomUUID());
        when(accounts.lockByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(accounts.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(account));
        when(encoder.matches("Actual#123", "hash")).thenReturn(true);
        when(encoder.encode(anyString())).thenReturn("nuevo-hash");
    }

    @Test
    void wrongCurrentPasswordIsRejectedAndNothingChanges() {
        assertThatThrownBy(() -> service.changePassword(EMAIL, "mala", "Nueva#456", null))
                .isInstanceOfSatisfying(BadRequestException.class, error -> assertThat(error.getCode()).isEqualTo("INVALID_PASSWORD"));
        verify(accounts, never()).save(any());
        verify(sessions, never()).revokeAll(any());
    }

    @Test
    void changingThePasswordKeepsTheCurrentSessionClosesTheOthersAndWarns() {
        service.changePassword(EMAIL, "Actual#123", "Nueva#456", "refresh-actual");

        assertThat(account.getPassword()).isEqualTo("nuevo-hash");
        verify(sessions).revokeOthersByRefreshToken("refresh-actual", EMAIL);
        verify(sessions, never()).revokeAll(any());
        verify(audit).record(eq(account.getId()), eq("PASSWORD_CHANGED"), eq("ACCOUNT"), eq(account.getId()), any());
        verify(events).publishEvent(any(SecurityNoticeRequested.class));
        verify(notifications).notify(eq(account.getId()), eq(NotificationKind.ACCOUNT_SECURITY), anyString(), anyString(),
                eq("/cuenta/seguridad"));
    }

    @Test
    void theNewPasswordMustBeDifferent() {
        when(encoder.matches("Actual#123", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(EMAIL, "Actual#123", "Actual#123", null))
                .isInstanceOfSatisfying(BadRequestException.class, error -> assertThat(error.getCode()).isEqualTo("PASSWORD_UNCHANGED"));
    }

    @Test
    void emailChangeSendsACodeToTheNewAddressOnly() {
        when(accounts.findByEmailIgnoreCase("nuevo@correo.co")).thenReturn(Optional.empty());
        when(tokenService.newCode()).thenReturn("123456");
        when(tokenService.hashVerificationCode(eq(account.getId()), anyString())).thenReturn("h");

        AccountManagementService.EmailChangeRequested result = service.requestEmailChange(EMAIL, " Nuevo@Correo.co ", "Actual#123");

        assertThat(result.sent()).isTrue();
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOfSatisfying(TwoFactorCodeRequested.class,
                code -> assertThat(code.email()).isEqualTo("nuevo@correo.co"));
    }

    @Test
    void emailTakenOrUnchangedIsRejected() {
        when(accounts.findByEmailIgnoreCase("otra@correo.co")).thenReturn(Optional.of(new Account()));

        assertThatThrownBy(() -> service.requestEmailChange(EMAIL, "otra@correo.co", "Actual#123"))
                .isInstanceOfSatisfying(ConflictException.class, error -> assertThat(error.getCode()).isEqualTo("EMAIL_ALREADY_EXISTS"));
        assertThatThrownBy(() -> service.requestEmailChange(EMAIL, EMAIL, "Actual#123"))
                .isInstanceOfSatisfying(BadRequestException.class, error -> assertThat(error.getCode()).isEqualTo("EMAIL_UNCHANGED"));
    }

    @Test
    void passwordAccountsMustConfirmThePasswordButSocialAccountsMayNot() {
        when(accounts.findByEmailIgnoreCase("nuevo@correo.co")).thenReturn(Optional.empty());
        when(tokenService.hashVerificationCode(any(), anyString())).thenReturn("h");

        assertThatThrownBy(() -> service.requestEmailChange(EMAIL, "nuevo@correo.co", null))
                .isInstanceOfSatisfying(BadRequestException.class, error -> assertThat(error.getCode()).isEqualTo("INVALID_PASSWORD"));

        when(identities.existsByAccount_Id(account.getId())).thenReturn(true);
        assertThat(service.requestEmailChange(EMAIL, "nuevo@correo.co", null).sent()).isTrue();
    }

    @Test
    void confirmingWithTheRightCodeChangesTheEmailAndClosesEverySession() {
        AccountToken token = AccountToken.builder().token("ok").expirationAt(LocalDateTime.now().plusMinutes(5)).build();
        when(tokens.findActive(eq(account.getId()), eq("EMAIL_CHANGE"), any())).thenReturn(List.of(token));
        when(tokenService.hashVerificationCode(account.getId(), "EMAIL_CHANGE:nuevo@correo.co:123456")).thenReturn("ok");
        when(accounts.findByEmailIgnoreCase("nuevo@correo.co")).thenReturn(Optional.empty());

        service.confirmEmailChange(EMAIL, "nuevo@correo.co", "123456");

        assertThat(account.getEmail()).isEqualTo("nuevo@correo.co");
        assertThat(token.isUsed()).isTrue();
        verify(sessions).revokeAll(account.getId());
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOfSatisfying(SecurityNoticeRequested.class,
                notice -> assertThat(notice.email()).isEqualTo(EMAIL));
    }

    @Test
    void aWrongCodeCountsAttemptsAndTheFifthInvalidatesIt() {
        AccountToken token = AccountToken.builder().token("ok").attempts((short) 4)
                .expirationAt(LocalDateTime.now().plusMinutes(5)).build();
        when(tokens.findActive(eq(account.getId()), eq("EMAIL_CHANGE"), any())).thenReturn(List.of(token));
        when(tokenService.hashVerificationCode(any(), anyString())).thenReturn("otro");

        assertThatThrownBy(() -> service.confirmEmailChange(EMAIL, "nuevo@correo.co", "000000"))
                .isInstanceOf(TooManyRequestsException.class);
        assertThat(token.isUsed()).isTrue();
        assertThat(account.getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void unlinkingWithoutTheRightPasswordKeepsTheOnlySignInMethod() {
        assertThatThrownBy(() -> service.unlink(EMAIL, "google", "mala"))
                .isInstanceOfSatisfying(ConflictException.class, error -> assertThat(error.getCode()).isEqualTo("LAST_SIGN_IN_METHOD"));
        verify(identities, never()).deleteAll(any());
    }

    @Test
    void storeOwnersCannotRequestDeletion() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(account.getPerson().getId()))).thenReturn(true);

        assertThatThrownBy(() -> service.requestDeletion(EMAIL, "Actual#123", "ya no la uso"))
                .isInstanceOfSatisfying(ConflictException.class, error -> assertThat(error.getCode()).isEqualTo("STORE_OWNER_CANNOT_DELETE"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void theOldAddressNoticeMasksTheNewEmail() {
        assertThat(AccountManagementService.mask("nuevo@correo.co")).isEqualTo("nu***@correo.co");
        assertThat(AccountManagementService.mask("a@b.co")).isEqualTo("a***@b.co");
    }
}
