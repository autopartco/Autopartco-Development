package com.autopartco.backend.core.identity.internal;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.shared.security.TokenHashing;


@ExtendWith(MockitoExtension.class)
class AccountSessionServiceTest {
    @Mock
    private AccountSessionRepository repository;
    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountSessionService accountSessionService;

    @Test
    void persistsSessionWithPreassignedJwtSessionIdAsNewEntity() {
        UUID sessionId = UUID.randomUUID();
        Account account = new Account();
        UUID accountId = UUID.randomUUID();
        account.setId(accountId);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);
        AccountSession session = AccountSession.builder()
                .id(sessionId)
                .account(account)
                .refreshTokenHash("hashed-token")
                .ipAddress("127.0.0.1")
                .userAgent("test-agent")
                .expiredAt(expiresAt)
                .build();
        when(repository.insertSession(
                sessionId,
                accountId,
                TokenHashing.sha256Hex("refresh-token"),
                "127.0.0.1",
                "test-agent",
                expiresAt
        )).thenReturn(1);
        when(repository.findById(sessionId)).thenReturn(Optional.of(session));

        AccountSession created = accountSessionService.create(
                sessionId,
                account,
                "refresh-token",
                "127.0.0.1",
                "test-agent",
                expiresAt
        );

        assertSame(session, created);
        verify(repository).insertSession(
                sessionId,
                accountId,
                TokenHashing.sha256Hex("refresh-token"),
                "127.0.0.1",
                "test-agent",
                expiresAt
        );
        verify(repository).findById(sessionId);
    }
}
