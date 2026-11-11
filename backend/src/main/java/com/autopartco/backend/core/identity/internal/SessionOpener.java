package com.autopartco.backend.core.identity.internal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.LoginResponse;

import lombok.AllArgsConstructor;

/**
 * Último paso común de los logins (contraseña, social y código 2FA): emite tokens, registra la sesión y, si
 * corresponde, avisa del inicio de sesión desde un dispositivo nuevo. El llamador ya registró el intento y la auditoría.
 */
@Service
@AllArgsConstructor
public class SessionOpener {
    private final AuthTokenService authTokenService;
    private final AccountSessionService accountSessionService;
    private final LoginAlerts loginAlerts;

    @Transactional
    public LoginResponse open(Account account, String ipAddress, String userAgent) {
        boolean alert = loginAlerts.shouldAlert(account, userAgent);
        AuthTokenService.TokenPair tokens = authTokenService.issue(account);
        accountSessionService.create(
                tokens.sessionId(),
                account,
                tokens.response().refreshToken(),
                ipAddress,
                userAgent,
                tokens.refreshExpiresAt()
        );
        if (alert) {
            loginAlerts.publish(account, ipAddress, userAgent);
        }
        return tokens.response();
    }
}
