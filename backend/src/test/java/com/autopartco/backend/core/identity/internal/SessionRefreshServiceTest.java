package com.autopartco.backend.core.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
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

import com.autopartco.backend.core.identity.LoginResponse;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.error.UnauthorizedException;
import com.autopartco.backend.shared.security.TokenHashing;

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
        Account account = mock(Account.class);
        State activeState = new State();
        activeState.setCode("ACTIVE");
        when(account.getState()).thenReturn(activeState);

        AccountSession session = mock(AccountSession.class);
        when(session.getRevokedAt()).thenReturn(null);
        when(session.getExpiredAt()).thenReturn(LocalDateTime.now().plusDays(1));
        when(session.getAccount()).thenReturn(account);
        when(session.getId()).thenReturn(sessionId);
        when(sessionRepository.lockByRefreshTokenHash(TokenHashing.sha256Hex("old-token")))
                .thenReturn(Optional.of(session));

        LoginResponse response = new LoginResponse("new-access", "new-refresh", "Bearer", 300);
        when(authTokenService.issue(account, sessionId)).thenReturn(new AuthTokenService.TokenPair(
                response,
                LocalDateTime.now().plusDays(30),
                sessionId
        ));

        assertEquals(response, sessionRefreshService.refresh(" old-token "));
        verify(session).setRefreshTokenHash(TokenHashing.sha256Hex("new-refresh"));
    }

    @Test
    void rejectsExpiredRefreshTokens() {
        AccountSession session = mock(AccountSession.class);
        when(session.getRevokedAt()).thenReturn(null);
        when(session.getExpiredAt()).thenReturn(LocalDateTime.now().minusSeconds(1));
        when(sessionRepository.lockByRefreshTokenHash(TokenHashing.sha256Hex("old-token")))
                .thenReturn(Optional.of(session));

        assertThrows(UnauthorizedException.class, () -> sessionRefreshService.refresh("old-token"));
    }
}
