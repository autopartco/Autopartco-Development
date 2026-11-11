package com.autopartco.backend.core.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.identity.web.LoginRequest;
import com.autopartco.backend.shared.error.ForbiddenException;
import com.autopartco.backend.shared.error.UnauthorizedException;
import com.autopartco.backend.shared.persistence.State;

@ExtendWith(MockitoExtension.class)
class LoginUnverifiedTest {
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
    @Mock
    private AuditService auditService;

    @InjectMocks
    private LoginService loginService;

    @Test
    void pendingAccountWithCorrectPasswordGets403WithMaskedEmail() {
        Account account = pendingAccount();
        when(passwordEncoder.matches("Correct123!", "encoded")).thenReturn(true);

        ForbiddenException error = assertThrows(ForbiddenException.class,
                () -> loginService.login(new LoginRequest("luis@gmail.com", "Correct123!"), "192.0.2.1", "ua"));

        assertEquals("EMAIL_NOT_VERIFIED", error.getCode());
        assertEquals(Map.of("email", "lu***@gmail.com"), error.getDetails());
        verify(loginAttemptService, never()).record(any(), eq(true), any());
        verify(authTokenService, never()).issue(account);
    }

    @Test
    void pendingAccountWithWrongPasswordKeepsInvalidCredentials() {
        Account account = pendingAccount();
        when(passwordEncoder.matches("Wrong123!", "encoded")).thenReturn(false);

        assertThrows(UnauthorizedException.class,
                () -> loginService.login(new LoginRequest("luis@gmail.com", "Wrong123!"), "192.0.2.1", "ua"));

        verify(loginAttemptService).record(account, false, "192.0.2.1");
    }

    @Test
    void masksTheLocalPart() {
        assertEquals("a***@x.co", LoginService.maskEmail("a@x.co"));
        assertEquals("lu***@gmail.com", LoginService.maskEmail("luis@gmail.com"));
    }

    private Account pendingAccount() {
        Account account = mock(Account.class);
        State pending = new State();
        pending.setCode("PENDING");
        when(account.getId()).thenReturn(UUID.randomUUID());
        when(account.getState()).thenReturn(pending);
        when(account.getPassword()).thenReturn("encoded");
        lenient().when(account.getEmail()).thenReturn("luis@gmail.com");
        when(accountRepository.findByEmailIgnoreCase("luis@gmail.com")).thenReturn(Optional.of(account));
        return account;
    }
}
