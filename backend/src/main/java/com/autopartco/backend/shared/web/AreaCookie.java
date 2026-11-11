package com.autopartco.backend.shared.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.autopartco.backend.shared.location.ColombiaBounds;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Cookie funcional {@value #NAME}: área elegida por el comprador. La escribe el frontend (no es HttpOnly) con
 * {@code encodeURIComponent("lat,lng,cityCode")}, p. ej. {@code 4.60%2C-74.10%2C}; lat y lng redondeados a 0,01 y
 * {@code cityCode} (DIVIPOLA, 5 dígitos) opcional. El backend solo la lee como ubicación por defecto cuando la petición
 * no trae lat/lng; lo inválido (formato, fuera de Colombia, ciudad mal formada) se ignora sin error.
 * <p>
 * Si el frontend y la API están en sitios distintos, el navegador no la envía en los {@code fetch} cross-site: el
 * frontend manda lat/lng explícitos y esta lectura simplemente no aplica.
 */
public final class AreaCookie {
    public static final String NAME = "ap_area";
    static final int MAX_LENGTH = 64;

    /** Área válida de la cookie. {@code cityCode} nulo si venía vacío. */
    public record Area(BigDecimal lat, BigDecimal lng, String cityCode) {
    }

    private AreaCookie() {
    }

    public static Optional<Area> read(HttpServletRequest request) {
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

    /** {@code lat,lng[,cityCode]}, codificado con URL o no. */
    public static Optional<Area> parse(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_LENGTH * 3) {
            return Optional.empty();
        }
        String value;
        try {
            value = URLDecoder.decode(raw.strip(), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
        if (value.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        String[] parts = value.split(",", -1);
        if (parts.length < 2 || parts.length > 3) {
            return Optional.empty();
        }
        BigDecimal lat = number(parts[0]);
        BigDecimal lng = number(parts[1]);
        if (lat == null || lng == null || !ColombiaBounds.contains(lat, lng)) {
            return Optional.empty();
        }
        String city = parts.length == 3 ? parts[2].strip() : "";
        if (!city.isEmpty() && !city.matches("\\d{5}")) {
            return Optional.empty();
        }
        return Optional.of(new Area(lat.setScale(2, RoundingMode.HALF_UP), lng.setScale(2, RoundingMode.HALF_UP),
                city.isEmpty() ? null : city));
    }

    private static BigDecimal number(String text) {
        String trimmed = text.strip();
        if (!trimmed.matches("-?\\d{1,3}(\\.\\d{1,6})?")) {
            return null;
        }
        return new BigDecimal(trimmed);
    }
}
