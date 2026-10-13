package com.autopartco.backend.core.identity.internal;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.LoginResponse;
import com.autopartco.backend.shared.error.UnauthorizedException;
import com.autopartco.backend.shared.persistence.StateCodes;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class SessionRefreshService {
    private final AccountSessionRepository sessionRepository;
    private final AuthTokenService authTokenService;

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public LoginResponse refresh(String rawRefreshToken) {
        String tokenHash = RefreshTokenHash.of(rawRefreshToken.trim());
        AccountSession session = sessionRepository.lockByRefreshTokenHash(tokenHash)
                .orElseThrow(this::invalidRefreshToken);

        LocalDateTime now = LocalDateTime.now();
        if (session.getRevokedAt() != null || !session.getExpiredAt().isAfter(now)) {
            throw invalidRefreshToken();
        }

        if (session.getAccount().getState() == null
                || !StateCodes.ACTIVE.equals(session.getAccount().getState().getCode())) {
            session.setRevokedAt(now);
            throw invalidRefreshToken();
        }

        AuthTokenService.TokenPair pair = authTokenService.issue(session.getAccount(), session.getId());
        session.setRefreshTokenHash(RefreshTokenHash.of(pair.response().refreshToken()));
        session.setExpiredAt(pair.refreshExpiresAt());
        return pair.response();
    }

    private UnauthorizedException invalidRefreshToken() {
        return new UnauthorizedException("INVALID_REFRESH_TOKEN", "El refresh token no es válido.");
    }
}
