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

        return new VisibilityReport(range.from(), range.to(), profile.views(), profile.visitors(), product.views(),
                product.visitors(), shares, bySource, topProducts, daily, whatsappClicks, directionsClicks, byBranch);
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
