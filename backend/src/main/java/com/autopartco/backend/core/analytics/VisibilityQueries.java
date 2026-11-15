package com.autopartco.backend.core.analytics;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

/** Agregados de {@code store_visibility_events}. No comprueba permisos: lo hace el caso de uso del portal. */
@Service
@AllArgsConstructor
public class VisibilityQueries {
    static final int TOP_PRODUCTS = 20;

    private static final String RANGE = " e.store_id = :storeId AND e.day BETWEEN :from AND :to ";

    static final String TOTALS_SQL = """
            SELECT e.kind, count(*) AS views, count(DISTINCT (e.day, e.visitor_hash)) AS visitors
            FROM store_visibility_events e
            WHERE""" + RANGE + "GROUP BY e.kind";

    /** Visitas = perfil + publicaciones; los compartidos y los clics se cuentan aparte. */
    static final String BY_SOURCE_SQL = """
            SELECT e.source,
                   count(*) FILTER (WHERE e.kind IN ('PROFILE_VIEW', 'PRODUCT_VIEW')) AS views,
                   count(DISTINCT (e.day, e.visitor_hash)) FILTER (WHERE e.kind IN ('PROFILE_VIEW', 'PRODUCT_VIEW'))
                       AS visitors,
                   count(*) FILTER (WHERE e.kind = 'SHARE') AS shares,
                   count(*) FILTER (WHERE e.kind = 'WHATSAPP_CLICK') AS whatsapp_clicks
            FROM store_visibility_events e
            WHERE""" + RANGE + """
            GROUP BY e.source
            ORDER BY views DESC, shares DESC, whatsapp_clicks DESC, e.source
            """;

    static final String TOP_PRODUCTS_SQL = """
            SELECT e.product_id, p.name,
                   count(*) FILTER (WHERE e.kind = 'PRODUCT_VIEW') AS views,
                   count(DISTINCT (e.day, e.visitor_hash)) FILTER (WHERE e.kind = 'PRODUCT_VIEW') AS visitors,
                   count(*) FILTER (WHERE e.kind = 'SHARE') AS shares,
                   count(*) FILTER (WHERE e.kind = 'WHATSAPP_CLICK') AS whatsapp_clicks
            FROM store_visibility_events e
            JOIN products p ON p.id = e.product_id
            WHERE e.product_id IS NOT NULL AND""" + RANGE + """
            GROUP BY e.product_id, p.name
            ORDER BY views DESC, shares DESC, whatsapp_clicks DESC, p.name
            LIMIT\s""" + TOP_PRODUCTS;

    static final String DAILY_SQL = """
            SELECT CAST(d AS date) AS day,
                   count(e.id) FILTER (WHERE e.kind = 'PROFILE_VIEW') AS profile_views,
                   count(e.id) FILTER (WHERE e.kind = 'PRODUCT_VIEW') AS product_views,
                   count(e.id) FILTER (WHERE e.kind = 'WHATSAPP_CLICK') AS whatsapp_clicks
            FROM generate_series(CAST(:from AS date), CAST(:to AS date), interval '1 day') AS d
            LEFT JOIN store_visibility_events e ON e.store_id = :storeId AND e.day = CAST(d AS date)
            GROUP BY d
            ORDER BY d
            """;

    static final String BY_BRANCH_SQL = """
            SELECT e.branch_id, b.name,
                   count(*) FILTER (WHERE e.kind = 'WHATSAPP_CLICK') AS whatsapp_clicks,
                   count(*) FILTER (WHERE e.kind = 'DIRECTIONS_CLICK') AS directions_clicks
            FROM store_visibility_events e
            JOIN branches b ON b.id = e.branch_id AND b.store_id = e.store_id
            WHERE e.branch_id IS NOT NULL AND""" + RANGE + """
            GROUP BY e.branch_id, b.name
            ORDER BY whatsapp_clicks DESC, directions_clicks DESC, b.name
            """;

    static final int TOP_SERVICES = 20;

    /** Servicios con vistas, clics o citas en el rango; las citas cuentan por fecha de solicitud (hora de Bogotá). */
    static final String TOP_SERVICES_SQL = """
            SELECT v.id AS service_id, v.name,
                   coalesce(ev.views, 0) AS views,
                   coalesce(ev.whatsapp_clicks, 0) AS whatsapp_clicks,
                   coalesce(ev.directions_clicks, 0) AS directions_clicks,
                   coalesce(bk.bookings, 0) AS bookings
            FROM store_services v
            LEFT JOIN (
                SELECT e.service_id,
                       count(*) FILTER (WHERE e.kind = 'SERVICE_VIEW') AS views,
                       count(*) FILTER (WHERE e.kind = 'WHATSAPP_CLICK') AS whatsapp_clicks,
                       count(*) FILTER (WHERE e.kind = 'DIRECTIONS_CLICK') AS directions_clicks
                FROM store_visibility_events e
                WHERE e.service_id IS NOT NULL AND""" + RANGE + """
                GROUP BY e.service_id
            ) ev ON ev.service_id = v.id
            LEFT JOIN (
                SELECT b.service_id, count(*) AS bookings
                FROM service_bookings b
                WHERE b.store_id = :storeId
                  AND CAST(b.created_at AT TIME ZONE 'America/Bogota' AS date) BETWEEN :from AND :to
                GROUP BY b.service_id
            ) bk ON bk.service_id = v.id
            WHERE v.store_id = :storeId AND (ev.service_id IS NOT NULL OR bk.service_id IS NOT NULL)
            ORDER BY views DESC, bookings DESC, whatsapp_clicks DESC, v.name
            LIMIT\s""" + TOP_SERVICES;

    /**
     * Citas solicitadas en el rango y cuántas de ellas están hoy confirmadas o ya se cerraron. {@code confirmed} cuenta
     * las confirmadas y las que llegaron a {@code COMPLETED} o {@code NO_SHOW} (pasaron por confirmación).
     */
    static final String BOOKINGS_SQL = """
            SELECT count(*) AS requested,
                   count(*) FILTER (WHERE b.status IN ('CONFIRMED', 'COMPLETED', 'NO_SHOW')) AS confirmed,
                   count(*) FILTER (WHERE b.status = 'COMPLETED') AS completed,
                   count(*) FILTER (WHERE b.status = 'NO_SHOW') AS no_show
            FROM service_bookings b
            WHERE b.store_id = :storeId
              AND CAST(b.created_at AT TIME ZONE 'America/Bogota' AS date) BETWEEN :from AND :to
            """;

    private final JdbcClient jdbcClient;

    @Transactional(readOnly = true)
    public VisibilityReport report(UUID storeId, DateRange range) {
        List<Total> totals = params(TOTALS_SQL, storeId, range)
                .query((rs, row) -> new Total(rs.getString("kind"), rs.getLong("views"), rs.getLong("visitors")))
                .list();
        Total profile = total(totals, "PROFILE_VIEW");
        Total product = total(totals, "PRODUCT_VIEW");
        long shares = total(totals, "SHARE").views();
        long whatsappClicks = total(totals, "WHATSAPP_CLICK").views();
        long directionsClicks = total(totals, "DIRECTIONS_CLICK").views();
        long serviceViews = total(totals, "SERVICE_VIEW").views();

        List<VisibilityReport.SourceRow> bySource = params(BY_SOURCE_SQL, storeId, range)
                .query((rs, row) -> new VisibilityReport.SourceRow(source(rs.getString("source")),
                        rs.getLong("views"), rs.getLong("visitors"), rs.getLong("shares"),
                        rs.getLong("whatsapp_clicks")))
                .list();
        List<VisibilityReport.ProductRow> topProducts = params(TOP_PRODUCTS_SQL, storeId, range)
                .query((rs, row) -> new VisibilityReport.ProductRow(rs.getObject("product_id", UUID.class),
                        rs.getString("name"), rs.getLong("views"), rs.getLong("visitors"), rs.getLong("shares"),
                        rs.getLong("whatsapp_clicks")))
                .list();
        List<VisibilityReport.DayRow> daily = params(DAILY_SQL, storeId, range)
                .query((rs, row) -> new VisibilityReport.DayRow(rs.getObject("day", LocalDate.class),
                        rs.getLong("profile_views"), rs.getLong("product_views"), rs.getLong("whatsapp_clicks")))
                .list();
        List<VisibilityReport.BranchRow> byBranch = params(BY_BRANCH_SQL, storeId, range)
                .query((rs, row) -> new VisibilityReport.BranchRow(rs.getObject("branch_id", UUID.class),
                        rs.getString("name"), rs.getLong("whatsapp_clicks"), rs.getLong("directions_clicks")))
                .list();

        List<VisibilityReport.ServiceRow> topServices = params(TOP_SERVICES_SQL, storeId, range)
                .query((rs, row) -> new VisibilityReport.ServiceRow(rs.getObject("service_id", UUID.class),
                        rs.getString("name"), rs.getLong("views"), rs.getLong("whatsapp_clicks"),
                        rs.getLong("directions_clicks"), rs.getLong("bookings")))
                .list();
        VisibilityReport.Bookings bookings = params(BOOKINGS_SQL, storeId, range)
                .query((rs, row) -> new VisibilityReport.Bookings(rs.getLong("requested"), rs.getLong("confirmed"),
                        rs.getLong("completed"), rs.getLong("no_show")))
                .list().stream().findFirst().orElse(VisibilityReport.Bookings.NONE);

        return new VisibilityReport(range.from(), range.to(), profile.views(), profile.visitors(), product.views(),
                product.visitors(), shares, bySource, topProducts, daily, whatsappClicks, directionsClicks, byBranch,
                serviceViews, topServices, bookings);
    }

    private static Total total(List<Total> totals, String kind) {
        return totals.stream().filter(t -> kind.equals(t.kind())).findFirst().orElse(new Total(kind, 0, 0));
    }

    private record Total(String kind, long views, long visitors) {
    }

    private JdbcClient.StatementSpec params(String sql, UUID storeId, DateRange range) {
        return jdbcClient.sql(sql)
                .param("storeId", storeId)
                .param("from", range.from())
                .param("to", range.to());
    }

    /** Un valor desconocido (fila escrita por otra versión) se reporta como {@link TrafficSource#OTHER}. */
    static TrafficSource source(String value) {
        try {
            return TrafficSource.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException unknown) {
            return TrafficSource.OTHER;
        }
    }
}
