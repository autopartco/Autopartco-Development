package com.autopartco.backend.core.identity.web;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.autopartco.backend.shared.web.ApiPaths;

/**
 * Cookie del refresh token: HttpOnly (JavaScript no la lee), SameSite=Lax y solo para las rutas de
 * {@code /auth}. Así el navegador conserva la sesión entre pestañas y recargas sin exponer el token.
 */
@Component
public class RefreshCookie {
    public static final String NAME = "autopartco_refresh";
    private static final String PATH = ApiPaths.BASE + "/auth";

    private final boolean secure;
    private final Duration maxAge;

    public RefreshCookie(
            @Value("${app.auth.cookie-secure:false}") boolean secure,
            @Value("${app.tokens.refresh-expires-days:30}") long refreshExpiresDays
    ) {
        this.secure = secure;
        this.maxAge = Duration.ofDays(refreshExpiresDays);
    }

    public String issue(String refreshToken) {
        return base(refreshToken).maxAge(maxAge).build().toString();
    }

    /** Cabecera para borrar la cookie en el navegador (logout). */
    public String clear() {
        return base("").maxAge(Duration.ZERO).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(PATH);
    }
}
