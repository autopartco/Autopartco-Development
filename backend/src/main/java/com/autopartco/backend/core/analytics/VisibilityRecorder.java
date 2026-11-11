package com.autopartco.backend.core.analytics;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Registra visitas al perfil del almacén, a sus publicaciones, compartidos y clics de WhatsApp o «Cómo llegar» (estos
 * dos exigen sucursal del almacén) en {@code store_visibility_events}. Igual
 * que {@link TrafficRecorder}: el visitante es un SHA-256 de su id anónimo con sal diaria y nunca se guarda IP,
 * user-agent ni cuenta (Ley 1581). Los datos inválidos se ignoran sin error.
 */
@Service
public class VisibilityRecorder {
    public enum Kind { PROFILE_VIEW, PRODUCT_VIEW, SHARE, WHATSAPP_CLICK, DIRECTIONS_CLICK }

    /**
     * Solo almacenes publicados (almacén {@code ACTIVE} y, si la tiene, solicitud {@code ACTIVE}) y, con producto, un producto {@code ACTIVE} de ese
     * almacén: evita métricas de tiendas sin publicar, ids ajenos y errores de clave foránea con ids inventados.
     * {@code uq_sve_visitor_event} deja un evento por visitante, día, producto, sucursal, servicio y tipo ({@code ON CONFLICT DO NOTHING}):
     * repetir la llamada con el mismo id anónimo no suma. Con sucursal, esta debe ser de ese almacén; con servicio,
     * un servicio activo de ese almacén (un id ajeno inserta nada).
     */
    static final String INSERT_SQL = """
            INSERT INTO store_visibility_events (day, store_id, product_id, kind, source, visitor_hash, branch_id,
                                                 service_id)
            SELECT :day, s.id, CAST(:productId AS uuid), :kind, :source, :visitor, CAST(:branchId AS uuid),
                   CAST(:serviceId AS uuid)
            FROM store s
            JOIN states ss ON ss.id = s.state AND ss.code = 'ACTIVE'
            WHERE s.id = :storeId AND s.deleted_at IS NULL
              AND NOT EXISTS (
                  SELECT 1 FROM seller_applications sa
                  WHERE sa.store_id = s.id AND sa.deleted_at IS NULL AND sa.status <> 'ACTIVE')
              AND (CAST(:productId AS uuid) IS NULL OR EXISTS (
                  SELECT 1 FROM products p
                  JOIN states ps ON ps.id = p.state_id AND ps.code = 'ACTIVE'
                  WHERE p.id = CAST(:productId AS uuid) AND p.store_id = s.id AND p.deleted_at IS NULL))
              AND (CAST(:branchId AS uuid) IS NULL OR EXISTS (
                  SELECT 1 FROM branches b
                  WHERE b.id = CAST(:branchId AS uuid) AND b.store_id = s.id AND b.deleted_at IS NULL))
              AND (CAST(:serviceId AS uuid) IS NULL OR EXISTS (
                  SELECT 1 FROM store_services v
                  WHERE v.id = CAST(:serviceId AS uuid) AND v.store_id = s.id AND v.active))
            ON CONFLICT DO NOTHING
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
            String channel,
            UUID branchId,
            UUID serviceId
    ) {
    }

    /** @return {@code true} si se guardó el evento. */
    public boolean record(Visit visit) {
        Kind kind = kind(visit.kind());
        if (kind == null || visit.storeId() == null || visit.anonymousId() == null || visit.anonymousId().isBlank()
                || visit.anonymousId().length() > 100) {
            return false;
        }
        boolean click = kind == Kind.WHATSAPP_CLICK || kind == Kind.DIRECTIONS_CLICK;
        UUID productId = kind == Kind.PROFILE_VIEW ? null : visit.productId();
        if (kind == Kind.PRODUCT_VIEW && productId == null) {
            return false;
        }
        // Un clic exige la sucursal (la BD también lo exige); en el resto de tipos sucursal y servicio no aplican.
        if (click && visit.branchId() == null) {
            return false;
        }
        UUID branchId = click ? visit.branchId() : null;
        UUID serviceId = click ? visit.serviceId() : null;
        TrafficSource source = source(kind, visit, ownHost);
        LocalDate day = Analytics.today();
        return jdbcClient.sql(INSERT_SQL)
                .param("day", day)
                .param("storeId", visit.storeId())
                .param("productId", productId)
                .param("kind", kind.name())
                .param("source", source.name())
                .param("visitor", TrafficRecorder.sha256(salt + "|" + day + "|" + visit.anonymousId()))
                .param("branchId", branchId)
                .param("serviceId", serviceId)
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
