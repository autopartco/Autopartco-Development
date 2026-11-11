package com.autopartco.backend.core.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import com.autopartco.backend.core.identity.AccountConfigurations;
import com.autopartco.backend.core.identity.internal.social.AccountIdentityRepository;
import com.autopartco.backend.core.identity.web.TwoFactorSetupResponse;
import com.autopartco.backend.core.identity.web.TwoFactorStatusResponse;
import com.autopartco.backend.shared.error.BadRequestException;

class TwoFactorSettingsTest {
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AccountTokenRepository tokenRepository = mock(AccountTokenRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AccountIdentityRepository identityRepository = mock(AccountIdentityRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final List<AccountToken> saved = new ArrayList<>();
    private Account account;
    private TwoFactorSettingsService service;

    @BeforeEach
    void setUp() {
        service = new TwoFactorSettingsService(accountRepository, tokenRepository,
                new EmailVerificationTokenService(tokenRepository, null, new TokenMapper()), new TokenMapper(),
                eventPublisher, passwordEncoder, identityRepository, auditService,
                new TwoFactorDisableAttempts(com.autopartco.backend.shared.web.RateLimiters.inMemory(
                        java.time.Clock.systemUTC())));
        Person person = new Person();
        person.setName("Ana");
        person.setSurname("Pérez");
        account = new Account();
        account.setId(UUID.randomUUID());
        account.setEmail("ana@example.com");
        account.setPassword("encoded");
        account.setPerson(person);
        account.setConfiguration(Map.of("preferences", Map.of("theme", "dark")));
        when(accountRepository.lockByEmail("ana@example.com")).thenReturn(Optional.of(account));
        when(tokenRepository.save(any(AccountToken.class))).thenAnswer(invocation -> {
            AccountToken token = invocation.getArgument(0);
            if (token.getId() == null) {
                token.setId(UUID.randomUUID());
                token.setCreatedAt(LocalDateTime.now());
                saved.add(token);
            }
            return token;
        });
        when(tokenRepository.findActive(eq(account.getId()), eq(TwoFactorSettingsService.PURPOSE), any()))
                .thenAnswer(invocation -> saved.stream().filter(token -> !token.isUsed()).toList().reversed());
    }

    @Test
    void confirmingTheCodeEnablesTwoFactorAndKeepsOtherSettings() {
        TwoFactorSetupResponse sent = service.request("ana@example.com", TwoFactorSettingsService.ENABLE);
        assertThat(sent.sent()).isTrue();

        TwoFactorStatusResponse status = service.confirm("ana@example.com", sentCode(), TwoFactorSettingsService.ENABLE);

        assertThat(status.enabled()).isTrue();
        assertThat(AccountConfigurations.twoFactorEnabled(account.getConfiguration())).isTrue();
        assertEquals(Map.of("theme", "dark"), account.getConfiguration().get("preferences"));
        verify(auditService).record(eq(account.getId()), eq("TWO_FACTOR_ENABLED"), eq("ACCOUNT"), eq(account.getId()), any());
    }

    @Test
    void wrongCodeDoesNotEnable() {
        service.request("ana@example.com", TwoFactorSettingsService.ENABLE);
        String wrong = sentCode().equals("000000") ? "111111" : "000000";

        assertEquals("INVALID_TWO_FACTOR_CODE", assertThrows(BadRequestException.class,
                () -> service.confirm("ana@example.com", wrong, TwoFactorSettingsService.ENABLE)).getCode());
        assertThat(AccountConfigurations.twoFactorEnabled(account.getConfiguration())).isFalse();
    }

    @Test
    void disableNeedsThePassword() {
        account.setConfiguration(AccountConfigurations.withTwoFactor(Map.of(), true));
        when(passwordEncoder.matches("Wrong!", "encoded")).thenReturn(false);
        when(passwordEncoder.matches("Correct123!", "encoded")).thenReturn(true);

        assertEquals("INVALID_PASSWORD", assertThrows(BadRequestException.class,
                () -> service.disableWithPassword("ana@example.com", "Wrong!")).getCode());
        assertThat(AccountConfigurations.twoFactorEnabled(account.getConfiguration())).isTrue();

        TwoFactorStatusResponse status = service.disableWithPassword("ana@example.com", "Correct123!");
        assertThat(status.enabled()).isFalse();
        verify(auditService).record(eq(account.getId()), eq("TWO_FACTOR_DISABLED"), eq("ACCOUNT"), eq(account.getId()), any());
    }

    @Test
    void fiveWrongPasswordsLockDisableEvenWithTheRightPassword() {
        account.setConfiguration(AccountConfigurations.withTwoFactor(Map.of(), true));
        when(passwordEncoder.matches("Wrong!", "encoded")).thenReturn(false);
        when(passwordEncoder.matches("Correct123!", "encoded")).thenReturn(true);

        for (int i = 0; i < TwoFactorDisableAttempts.MAX_FAILURES; i++) {
            assertEquals("INVALID_PASSWORD", assertThrows(BadRequestException.class,
                    () -> service.disableWithPassword("ana@example.com", "Wrong!")).getCode());
        }
        com.autopartco.backend.shared.error.TooManyRequestsException locked = assertThrows(
                com.autopartco.backend.shared.error.TooManyRequestsException.class,
                () -> service.disableWithPassword("ana@example.com", "Correct123!"));
        assertEquals("TWO_FACTOR_DISABLE_LOCKED", locked.getCode());
        assertThat(AccountConfigurations.twoFactorEnabled(account.getConfiguration())).isTrue();
    }

    @Test
    void socialAccountsCanDisableWithACodeThatOnlyWorksForThatAction() {
        account.setConfiguration(AccountConfigurations.withTwoFactor(Map.of(), true));
        when(identityRepository.existsByAccount_Id(account.getId())).thenReturn(true);

        service.request("ana@example.com", TwoFactorSettingsService.DISABLE);
        String code = sentCode();
        assertThrows(BadRequestException.class,
                () -> service.confirm("ana@example.com", code, TwoFactorSettingsService.ENABLE));

        TwoFactorStatusResponse status = service.confirm("ana@example.com", code, TwoFactorSettingsService.DISABLE);
        assertThat(status.enabled()).isFalse();
    }

    @Test
    void passwordAccountsCannotDisableByCode() {
        account.setConfiguration(AccountConfigurations.withTwoFactor(Map.of(), true));
        when(identityRepository.existsByAccount_Id(account.getId())).thenReturn(false);

        assertEquals("PASSWORD_REQUIRED", assertThrows(BadRequestException.class,
                () -> service.request("ana@example.com", TwoFactorSettingsService.DISABLE)).getCode());
    }

    private String sentCode() {
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publishEvent(events.capture());
        return ((TwoFactorCodeRequested) events.getAllValues().get(events.getAllValues().size() - 1)).code();
    }
}
