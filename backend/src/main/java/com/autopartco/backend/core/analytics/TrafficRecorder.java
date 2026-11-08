package com.autopartco.backend.core.analytics;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Registra una visita del marketplace sin datos personales: la ruta se agrupa ({@code /producto/:id}), el visitante
 * se guarda como SHA-256 de su id anónimo con sal diaria (no se puede seguir entre días) y del referente solo el
 * dominio. Nunca IP ni user-agent completo (Ley 1581).
 */
@Service
public class TrafficRecorder {
    static final int MAX_PATH = 60;
    private static final Pattern ID_SEGMENT = Pattern.compile(
            "^([0-9a-fA-F-]{32,36}|\\d+|[0-9a-zA-Z]{20,})$");
    private static final Pattern MOBILE = Pattern.compile("(?i)mobile|android|iphone|ipad|ipod");
    /** Rutas que no se miden: consola, portal y autenticación. */
    private static final List<String> EXCLUDED = List.of("/admin", "/portal", "/ingresar", "/registro", "/recuperar");

    private final JdbcClient jdbcClient;
    private final String salt;
    private final String ownHost;

    public TrafficRecorder(
            JdbcClient jdbcClient,
            @Value("${app.traffic.salt:${app.public-base-url:autopartco}}") String salt,
            @Value("${app.frontend-base-url:}") String frontendBaseUrl
    ) {
        this.jdbcClient = jdbcClient;
        this.salt = salt;
        this.ownHost = host(frontendBaseUrl);
    }

    /** @return {@code false} si la ruta no se mide o el id anónimo no es válido (se ignora sin error). */
    public boolean record(String anonymousId, String path, String referrer, String userAgent, UUID accountId) {
        String group = group(path);
        if (group == null || anonymousId == null || anonymousId.isBlank() || anonymousId.length() > 100) {
            return false;
        }
        LocalDate day = Analytics.today();
        String referrerHost = host(referrer);
        if (referrerHost != null && referrerHost.equals(ownHost)) {
            referrerHost = null;
        }
        jdbcClient.sql("""
                        INSERT INTO traffic_events (day, path_group, visitor_hash, account_id, device, referrer_host)
                        VALUES (:day, :path, :visitor, :account, :device, :referrer)
                        """)
                .param("day", day)
                .param("path", group)
                .param("visitor", sha256(salt + "|" + day + "|" + anonymousId))
                .param("account", accountId)
                .param("device", device(userAgent))
                .param("referrer", referrerHost)
                .update();
        return true;
    }

    /** Ruta normalizada: sin consulta ni fragmento, en minúsculas, ids reemplazados por {@code :id}. */
    static String group(String path) {
        if (path == null || path.isBlank() || !path.startsWith("/")) {
            return null;
        }
        String clean = path.split("[?#]", 2)[0].toLowerCase(Locale.ROOT);
        for (String excluded : EXCLUDED) {
            if (clean.equals(excluded) || clean.startsWith(excluded + "/")) {
                return null;
            }
        }
        StringBuilder grouped = new StringBuilder();
        for (String segment : clean.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            grouped.append('/').append(ID_SEGMENT.matcher(segment).matches() ? ":id" : segment);
        }
        String result = grouped.isEmpty() ? "/" : grouped.toString();
        return result.length() > MAX_PATH ? result.substring(0, MAX_PATH) : result;
    }

    static String device(String userAgent) {
        return userAgent != null && MOBILE.matcher(userAgent).find() ? "mobile" : "desktop";
    }

    static String host(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(url.trim()).getHost();
            if (host == null) {
                return null;
            }
            host = host.toLowerCase(Locale.ROOT);
            host = host.startsWith("www.") ? host.substring(4) : host;
            return host.length() > 255 ? host.substring(0, 255) : host;
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
