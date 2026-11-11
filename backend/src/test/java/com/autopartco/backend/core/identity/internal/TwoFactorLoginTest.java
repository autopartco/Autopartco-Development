package com.autopartco.backend.core.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.identity.LoginResponse;
import com.autopartco.backend.core.identity.web.LoginRequest;
import com.autopartco.backend.core.identity.web.TwoFactorResendResponse;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.TooManyRequestsException;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.persistence.StateCodes;

class TwoFactorLoginTest {
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
    private final AccountStatusService accountStatusService = mock(AccountStatusService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AccountTokenRepository tokenRepository = mock(AccountTokenRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final SessionOpener sessionOpener = mock(SessionOpener.class);
    private final LoginResponse tokens = new LoginResponse("access", "refresh", "Bearer", 900);

    private final List<AccountToken> savedTokens = new ArrayList<>();
    private Account account;
    private TwoFactorLoginService twoFactor;
    private LoginService loginService;

    @BeforeEach
    void setUp() {
        EmailVerificationTokenService tokenService = new EmailVerificationTokenService(tokenRepository, null, new TokenMapper());
        twoFactor = new TwoFactorLoginService(tokenRepository, tokenService, new TokenMapper(), eventPublisher,
                loginAttemptService, accountStatusService, auditService, sessionOpener);
        loginService = new LoginService(accountRepository, passwordEncoder, loginAttemptService, accountStatusService,
                auditService, twoFactor, sessionOpener);

        State active = new State();
        active.setCode(StateCodes.ACTIVE);
        Person person = new Person();
        person.setName("Ana");
        person.setSurname("Pérez");
        account = new Account();
        account.setId(UUID.randomUUID());
        account.setEmail("ana@example.com");
        account.setPassword("encoded");
        account.setState(active);
        account.setPerson(person);
        account.setConfiguration(Map.of("security", Map.of("twoFactor", Map.of("enabled", true, "method", "EMAIL"))));

        when(accountRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("Correct123!", "encoded")).thenReturn(true);
        when(tokenRepository.save(any(AccountToken.class))).thenAnswer(invocation -> {
            AccountToken token = invocation.getArgument(0);
            if (token.getId() == null) {
                token.setId(UUID.randomUUID());
                token.setCreatedAt(LocalDateTime.now());
                savedTokens.add(token);
            }
            return token;
        });
        when(tokenRepository.lockByIdAndPurpose(any(), eq(TwoFactorLoginService.PURPOSE))).thenAnswer(invocation ->
                savedTokens.stream().filter(token -> token.getId().equals(invocation.getArgument(0))).findFirst());
        when(sessionOpener.open(account, "192.0.2.1", "ua")).thenReturn(tokens);
    }

    @Test
    void loginWithTwoFactorReturnsChallengeWithoutTokens() {
        LoginResponse response = login();

        assertThat(response.twoFactorRequired()).isTrue();
        assertThat(response.accessToken()).isNull();
        assertThat(response.refreshToken()).isNull();
        assertThat(response.tokenType()).isNull();
        assertEquals(savedTokens.get(0).getId(), response.challengeId());
        assertThat(response.expiresAt()).isNotNull();
        assertEquals(TwoFactorLoginService.PURPOSE, savedTokens.get(0).getPurpose());
        verify(sessionOpener, never()).open(any(), any(), any());
        verify(loginAttemptService, never()).record(any(), eq(true), any());
        verify(auditService, never()).record(any(), eq("LOGIN_SUCCEEDED"), anyString(), any(), any());
        assertThat(sentCode()).matches("\\d{6}");
    }

    @Test
    void correctCodeOpensSession() {
        LoginResponse challenge = login();

        LoginResponse response = twoFactor.verify(challenge.challengeId(), sentCode(), "192.0.2.1", "ua");

        assertSame(tokens, response);
        assertThat(savedTokens.get(0).isUsed()).isTrue();
        verify(loginAttemptService).record(account, true, "192.0.2.1");
        verify(auditService).record(eq(account.getId()), eq("LOGIN_SUCCEEDED"), eq("ACCOUNT"), eq(account.getId()), any());
        // El código ya no sirve una segunda vez.
        assertThrows(BadRequestException.class,
                () -> twoFactor.verify(challenge.challengeId(), sentCode(), "192.0.2.1", "ua"));
    }

    @Test
    void fiveWrongCodesGive429AndInvalidateTheChallenge() {
        LoginResponse challenge = login();
        String wrong = sentCode().equals("000000") ? "111111" : "000000";

        for (int attempt = 1; attempt <= 4; attempt++) {
            BadRequestException error = assertThrows(BadRequestException.class,
                    () -> twoFactor.verify(challenge.challengeId(), wrong, "192.0.2.1", "ua"));
            assertEquals("INVALID_TWO_FACTOR_CODE", error.getCode());
        }
        TooManyRequestsException exceeded = assertThrows(TooManyRequestsException.class,
                () -> twoFactor.verify(challenge.challengeId(), wrong, "192.0.2.1", "ua"));

        assertEquals("TWO_FACTOR_ATTEMPTS_EXCEEDED", exceeded.getCode());
        assertThat(savedTokens.get(0).isUsed()).isTrue();
        verify(loginAttemptService, times(5)).record(account, false, "192.0.2.1");
        verify(sessionOpener, never()).open(any(), any(), any());
        // Ni el código correcto sirve ya.
        assertThrows(BadRequestException.class,
                () -> twoFactor.verify(challenge.challengeId(), sentCode(), "192.0.2.1", "ua"));
    }

    @Test
    void failedCodesLockTheAccountLikeFailedLogins() {
        LoginResponse challenge = login();
        when(loginAttemptService.hasExceededFailedAttempts(account.getId())).thenReturn(true);
        String wrong = sentCode().equals("000000") ? "111111" : "000000";

        assertThrows(TooManyRequestsException.class,
                () -> twoFactor.verify(challenge.challengeId(), wrong, "192.0.2.1", "ua"));

        verify(accountStatusService).lock(account, "192.0.2.1", LoginAttemptService.MAX_FAILED_ATTEMPTS);
        assertThat(savedTokens.get(0).isUsed()).isTrue();
    }

    @Test
    void unknownOrExpiredChallengeIsInvalid() {
        assertEquals("INVALID_TWO_FACTOR_CODE", assertThrows(BadRequestException.class,
                () -> twoFactor.verify(UUID.randomUUID(), "123456", "192.0.2.1", "ua")).getCode());

        LoginResponse challenge = login();
        savedTokens.get(0).setExpirationAt(LocalDateTime.now().minusSeconds(1));
        assertEquals("INVALID_TWO_FACTOR_CODE", assertThrows(BadRequestException.class,
                () -> twoFactor.verify(challenge.challengeId(), sentCode(), "192.0.2.1", "ua")).getCode());
    }

    @Test
    void resendWaitsSixtySecondsThenIssuesANewChallenge() {
        LoginResponse challenge = login();
        AccountToken first = savedTokens.get(0);
        when(tokenRepository.findFirstByAccount_IdAndPurposeOrderByCreatedAtDesc(account.getId(), TwoFactorLoginService.PURPOSE))
                .thenAnswer(invocation -> Optional.of(savedTokens.get(savedTokens.size() - 1)));

        TwoFactorResendResponse cooling = twoFactor.resend(challenge.challengeId());
        assertEquals(challenge.challengeId(), cooling.challengeId());
        assertThat(cooling.retryAfterSeconds()).isBetween(1L, 60L);
        verify(eventPublisher, times(1)).publishEvent(any(TwoFactorCodeRequested.class));

        first.setCreatedAt(LocalDateTime.now().minusSeconds(61));
        TwoFactorResendResponse resent = twoFactor.resend(challenge.challengeId());

        assertThat(resent.challengeId()).isNotEqualTo(challenge.challengeId());
        assertEquals(60, resent.retryAfterSeconds());
        verify(eventPublisher, times(2)).publishEvent(any(TwoFactorCodeRequested.class));
        verify(tokenRepository, times(2)).markUsed(eq(account.getId()), eq(List.of(TwoFactorLoginService.PURPOSE)), any());
    }

    @Test
    void loginWithoutTwoFactorOpensSessionDirectly() {
        account.setConfiguration(Map.of());

        LoginResponse response = login();

        assertSame(tokens, response);
        assertThat(response.twoFactorRequired()).isFalse();
        verify(loginAttemptService).record(account, true, "192.0.2.1");
        assertThat(savedTokens).isEmpty();
    }

    private LoginResponse login() {
        return loginService.login(new LoginRequest("ana@example.com", "Correct123!"), "192.0.2.1", "ua");
    }

    private String sentCode() {
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(events.capture());
        return events.getAllValues().stream()
                .filter(TwoFactorCodeRequested.class::isInstance)
                .map(TwoFactorCodeRequested.class::cast)
                .reduce((first, second) -> second)
                .orElseThrow()
                .code();
    }
}
