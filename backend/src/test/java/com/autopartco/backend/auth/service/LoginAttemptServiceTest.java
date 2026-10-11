package com.autopartco.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.auth.entity.LoginAttempt;
import com.autopartco.backend.auth.mapper.LoginAttemptMapper;
import com.autopartco.backend.auth.repository.LoginAttemptRepository;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {
    @Mock
    private LoginAttemptRepository repository;

    @Mock
    private LoginAttemptMapper loginAttemptMapper;

    @InjectMocks
    private LoginAttemptService loginAttemptService;

    @Test
    void reportsExceededAfterThreeConsecutiveFailuresFromTheSameIp() {
        UUID accountId = UUID.randomUUID();
        when(repository.findByAccountId(any(), any()))
                .thenReturn(List.of(attempt(false, "192.0.2.10"),
                        attempt(false, "192.0.2.10"),
                        attempt(false, "192.0.2.10")));

        assertTrue(loginAttemptService.hasExceededFailedAttempts(accountId));
    }

    @Test
    void doesNotReportExceededBeforeThreeFailuresOrAfterASuccessOrIpChange() {
        UUID accountId = UUID.randomUUID();
        when(repository.findByAccountId(any(), any()))
                .thenReturn(List.of(attempt(false, "192.0.2.10"),
                        attempt(false, "192.0.2.10")))
                .thenReturn(List.of(attempt(false, "192.0.2.10"),
                        attempt(true, "192.0.2.10"),
                        attempt(false, "192.0.2.10")))
                .thenReturn(List.of(attempt(false, "192.0.2.10"),
                        attempt(false, "192.0.2.10"),
                        attempt(false, "192.0.2.11")));

        assertFalse(loginAttemptService.hasExceededFailedAttempts(accountId));
        assertFalse(loginAttemptService.hasExceededFailedAttempts(accountId));
        assertFalse(loginAttemptService.hasExceededFailedAttempts(accountId));
    }

    private LoginAttempt attempt(boolean successful, String ipAddress) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setSuccessful(successful);
        attempt.setIpAddress(ipAddress);
        return attempt;
    }
}
