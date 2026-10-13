package com.autopartco.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import com.autopartco.backend.auth.dto.LoginResponse;
import com.autopartco.backend.auth.entity.Account;
import com.autopartco.backend.auth.entity.AccountSession;
import com.autopartco.backend.auth.repository.AccountSessionRepository;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.error.UnauthorizedException;

@ExtendWith(MockitoExtension.class)
class SessionRefreshServiceTest {
    @Mock
    private AccountSessionRepository sessionRepository;

    @Mock
    private AuthTokenService authTokenService;

    @InjectMocks
    private SessionRefreshService sessionRefreshService;

    @Test
    void rotatesTokenHashAndReturnsNewTokenPairForSameSession() {
        UUID sessionId = UUID.randomUUID();
        Account account = org.mockito.Mockito.mock(Account.class);
        State activeState = new State();
        activeState.setCode("ACTIVE");
        when(account.getState()).thenReturn(activeState);

        AccountSession session = org.mockito.Mockito.mock(AccountSession.class);
        when(session.getRevokedAt()).thenReturn(null);
        when(session.getExpiredAt()).thenReturn(LocalDateTime.now().plusDays(1));
        when(session.getAccount()).thenReturn(account);
        when(session.getId()).thenReturn(sessionId);
        when(sessionRepository.findByRefreshTokenHashForUpdate(RefreshTokenHash.of("old-token")))
                .thenReturn(Optional.of(session));

        LoginResponse response = new LoginResponse("new-access", "new-refresh", "Bearer", 300);
        when(authTokenService.issue(account, sessionId)).thenReturn(new AuthTokenService.TokenPair(
                response,
                LocalDateTime.now().plusDays(30),
                sessionId
        ));

        assertEquals(response, sessionRefreshService.refresh(" old-token "));
        verify(session).setRefreshTokenHash(RefreshTokenHash.of("new-refresh"));
    }

    @Test
    void rejectsExpiredRefreshTokens() {
        AccountSession session = org.mockito.Mockito.mock(AccountSession.class);
        when(session.getRevokedAt()).thenReturn(null);
        when(session.getExpiredAt()).thenReturn(LocalDateTime.now().minusSeconds(1));
        when(sessionRepository.findByRefreshTokenHashForUpdate(RefreshTokenHash.of("old-token")))
                .thenReturn(Optional.of(session));

        assertThrows(UnauthorizedException.class, () -> sessionRefreshService.refresh("old-token"));
    }
}
