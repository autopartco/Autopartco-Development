package com.autopartco.backend.shared.web;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Cookie funcional {@value #NAME}: identificador anónimo del visitante (UUID aleatorio, sin datos personales), 1 año,
 * {@code HttpOnly}. Se emite en {@code /events/*} si no llega y la usan las métricas de visibilidad, los anuncios
 * (tope de frecuencia y eventos) y {@code POST /marketplace/orders} cuando el cliente no envía {@code anonymousId}
 * (el del cuerpo sigue teniendo prioridad, por compatibilidad).
 * <p>
 * {@code SameSite=None; Secure} para que viaje en los {@code fetch} con credenciales aunque el frontend y la API
 * estén en sitios distintos; en {@code localhost} va {@code SameSite=Lax} sin {@code Secure} (HTTP local).
 */
public final class VisitorCookie {
    public static final String NAME = "ap_aid";
    static final Duration MAX_AGE = Duration.ofDays(365);
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    private VisitorCookie() {
    }

    /** UUID de la cookie, si llega y es válido. */
    public static Optional<UUID> read(HttpServletRequest request) {
        Cookie[] cookies = request == null ? null : request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName())) {
                return parse(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    /** Lee la cookie o, si falta o es inválida, crea un id nuevo y añade {@code Set-Cookie} a la respuesta. */
    public static UUID ensure(HttpServletRequest request, HttpServletResponse response) {
        Optional<UUID> existing = read(request);
        if (existing.isPresent()) {
            return existing.get();
        }
        UUID id = UUID.randomUUID();
        response.addHeader(HttpHeaders.SET_COOKIE, issue(id, isLocal(request)).toString());
        return id;
    }

    static ResponseCookie issue(UUID id, boolean local) {
        return ResponseCookie.from(NAME, id.toString())
                .httpOnly(true)
                .secure(!local)
                .sameSite(local ? "Lax" : "None")
                .path("/")
                .maxAge(MAX_AGE)
                .build();
    }

    /**
     * Visitante para el tope de frecuencia de anuncios: cabecera de sesión anónima, la cookie o, sin ninguna, la IP.
     * Solo se usa como clave (en Redis, con hash); nunca se guarda.
     */
    public static String visitorKey(HttpServletRequest request, String sessionHeader) {
        if (sessionHeader != null && !sessionHeader.isBlank() && sessionHeader.length() <= 100) {
            return "s:" + sessionHeader.strip();
        }
        Optional<UUID> cookie = read(request);
        if (cookie.isPresent()) {
            return "a:" + cookie.get();
        }
        return "ip:" + request.getRemoteAddr();
    }

    static boolean isLocal(HttpServletRequest request) {
        String host = request.getServerName();
        return host != null && LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT));
    }

    private static Optional<UUID> parse(String value) {
        if (value == null || value.length() != 36) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
