package com.autopartco.backend.core.analytics;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Registra visitas al perfil del almacén, a sus publicaciones y compartidos en {@code store_visibility_events}. Igual
 * que {@link TrafficRecorder}: el visitante es un SHA-256 de su id anónimo con sal diaria y nunca se guarda IP,
 * user-agent ni cuenta (Ley 1581). Los datos inválidos se ignoran sin error.
 */
@Service
public class VisibilityRecorder {
    public enum Kind { PROFILE_VIEW, PRODUCT_VIEW, SHARE }

    /**
     * Solo si el almacén está activo y, con producto, el producto es de ese almacén: evita inflar métricas de otro
     * almacén con ids ajenos y errores de clave foránea con ids inventados.
     */
    static final String INSERT_SQL = """
            INSERT INTO store_visibility_events (day, store_id, product_id, kind, source, visitor_hash)
            SELECT :day, s.id, CAST(:productId AS uuid), :kind, :source, :visitor
            FROM store s
            JOIN states ss ON ss.id = s.state AND ss.code = 'ACTIVE'
            WHERE s.id = :storeId AND s.deleted_at IS NULL
              AND (CAST(:productId AS uuid) IS NULL OR EXISTS (
                  SELECT 1 FROM products p
                  WHERE p.id = CAST(:productId AS uuid) AND p.store_id = s.id AND p.deleted_at IS NULL))
            """;

    private final JdbcClient jdbcClient;
    private final String salt;
    private final String ownHost;

    public VisibilityRecorder(
            JdbcClient jdbcClient,
            @Value("${app.traffic.salt:${app.public-base-url:autopartco}}") String salt,
            @Value("${app.frontend-base-url:}") String frontendBaseUrl
    ) {
        this.jdbcClient = jdbcClient;
        this.salt = salt;
        this.ownHost = TrafficRecorder.host(frontendBaseUrl);
    }

    public record Visit(
            String anonymousId,
            String kind,
            UUID storeId,
            UUID productId,
            String ref,
            String utmSource,
            String referrer,
            String channel
    ) {
    }

    /** @return {@code true} si se guardó el evento. */
    public boolean record(Visit visit) {
        Kind kind = kind(visit.kind());
        if (kind == null || visit.storeId() == null || visit.anonymousId() == null || visit.anonymousId().isBlank()
                || visit.anonymousId().length() > 100) {
            return false;
        }
        UUID productId = kind == Kind.PROFILE_VIEW ? null : visit.productId();
        if (kind == Kind.PRODUCT_VIEW && productId == null) {
            return false;
        }
        TrafficSource source = source(kind, visit, ownHost);
        LocalDate day = Analytics.today();
        return jdbcClient.sql(INSERT_SQL)
                .param("day", day)
                .param("storeId", visit.storeId())
                .param("productId", productId)
                .param("kind", kind.name())
                .param("source", source.name())
                .param("visitor", TrafficRecorder.sha256(salt + "|" + day + "|" + visit.anonymousId()))
                .update() > 0;
    }

    /** Un compartido se clasifica por el canal elegido; una visita, por {@code ref}, {@code utm_source} y referente. */
    static TrafficSource source(Kind kind, Visit visit, String ownHost) {
        if (kind == Kind.SHARE) {
            TrafficSource channel = TrafficSource.fromRef(visit.channel());
            return channel == null ? TrafficSource.OTHER : channel;
        }
        return TrafficSource.resolve(visit.ref(), visit.utmSource(), visit.referrer(), ownHost);
    }

    private static Kind kind(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Kind.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
