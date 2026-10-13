package com.autopartco.backend.core.identity.internal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.autopartco.backend.core.identity.web.LoginRequest;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.error.UnauthorizedException;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {
    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private AccountStatusService accountStatusService;

    @Mock
    private AuthTokenService authTokenService;

    @Mock
    private AccountSessionService accountSessionService;

    @InjectMocks
    private LoginService loginService;

    @Test
    void locksAccountOnlyWhenTheFailedAttemptThresholdIsReached() {
        UUID accountId = UUID.randomUUID();
        Account account = activeAccount(accountId);
        when(accountRepository.findByEmailIgnoreCase("ana@example.com"))
                .thenReturn(Optional.of(account));
        when(accountStatusService.isLocked(account)).thenReturn(false);
        when(loginAttemptService.hasExceededFailedAttempts(accountId))
                .thenReturn(false, true);
        when(passwordEncoder.matches("WrongPassword123!", "encoded-password")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> loginService.login(
                new LoginRequest("Ana@Example.com", "WrongPassword123!"),
                "192.0.2.10",
                "test-agent"
        ));

        verify(loginAttemptService).record(account, false, "192.0.2.10");
        verify(accountStatusService).lock(account, "192.0.2.10", 3);
        verify(accountSessionService, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void doesNotLockAccountForAFailedAttemptBelowTheThreshold() {
        UUID accountId = UUID.randomUUID();
        Account account = activeAccount(accountId);
        when(accountRepository.findByEmailIgnoreCase("ana@example.com"))
                .thenReturn(Optional.of(account));
        when(accountStatusService.isLocked(account)).thenReturn(false);
        when(loginAttemptService.hasExceededFailedAttempts(accountId))
                .thenReturn(false, false);
        when(passwordEncoder.matches("WrongPassword123!", "encoded-password")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> loginService.login(
                new LoginRequest("Ana@Example.com", "WrongPassword123!"),
                "192.0.2.10",
                "test-agent"
        ));

        verify(loginAttemptService).record(account, false, "192.0.2.10");
        verify(accountStatusService, never()).lock(any(), any(), anyInt());
    }

    private Account activeAccount(UUID accountId) {
        Account account = org.mockito.Mockito.mock(Account.class);
        State activeState = new State();
        activeState.setCode("ACTIVE");
        when(account.getId()).thenReturn(accountId);
        when(account.getState()).thenReturn(activeState);
        when(account.getPassword()).thenReturn("encoded-password");
        return account;
    }
}
