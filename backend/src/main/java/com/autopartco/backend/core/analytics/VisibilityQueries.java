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

    static final String BY_SOURCE_SQL = """
            SELECT e.source,
                   count(*) FILTER (WHERE e.kind <> 'SHARE') AS views,
                   count(DISTINCT (e.day, e.visitor_hash)) FILTER (WHERE e.kind <> 'SHARE') AS visitors,
                   count(*) FILTER (WHERE e.kind = 'SHARE') AS shares
            FROM store_visibility_events e
            WHERE""" + RANGE + """
            GROUP BY e.source
            ORDER BY views DESC, shares DESC, e.source
            """;

    static final String TOP_PRODUCTS_SQL = """
            SELECT e.product_id, p.name,
                   count(*) FILTER (WHERE e.kind = 'PRODUCT_VIEW') AS views,
                   count(DISTINCT (e.day, e.visitor_hash)) FILTER (WHERE e.kind = 'PRODUCT_VIEW') AS visitors,
                   count(*) FILTER (WHERE e.kind = 'SHARE') AS shares
            FROM store_visibility_events e
            JOIN products p ON p.id = e.product_id
            WHERE e.product_id IS NOT NULL AND""" + RANGE + """
            GROUP BY e.product_id, p.name
            ORDER BY views DESC, shares DESC, p.name
            LIMIT\s""" + TOP_PRODUCTS;

    static final String DAILY_SQL = """
            SELECT CAST(d AS date) AS day,
                   count(e.id) FILTER (WHERE e.kind = 'PROFILE_VIEW') AS profile_views,
                   count(e.id) FILTER (WHERE e.kind = 'PRODUCT_VIEW') AS product_views
            FROM generate_series(CAST(:from AS date), CAST(:to AS date), interval '1 day') AS d
            LEFT JOIN store_visibility_events e ON e.store_id = :storeId AND e.day = CAST(d AS date)
            GROUP BY d
            ORDER BY d
            """;

    private final JdbcClient jdbcClient;

    @Transactional(readOnly = true)
    public VisibilityReport report(UUID storeId, DateRange range) {
        record Total(String kind, long views, long visitors) {
        }
        List<Total> totals = params(TOTALS_SQL, storeId, range)
                .query((rs, row) -> new Total(rs.getString("kind"), rs.getLong("views"), rs.getLong("visitors")))
                .list();
        Total profile = totals.stream().filter(t -> "PROFILE_VIEW".equals(t.kind())).findFirst()
                .orElse(new Total("PROFILE_VIEW", 0, 0));
        Total product = totals.stream().filter(t -> "PRODUCT_VIEW".equals(t.kind())).findFirst()
                .orElse(new Total("PRODUCT_VIEW", 0, 0));
        long shares = totals.stream().filter(t -> "SHARE".equals(t.kind())).mapToLong(Total::views).sum();

        List<VisibilityReport.SourceRow> bySource = params(BY_SOURCE_SQL, storeId, range)
                .query((rs, row) -> new VisibilityReport.SourceRow(source(rs.getString("source")),
                        rs.getLong("views"), rs.getLong("visitors"), rs.getLong("shares")))
                .list();
        List<VisibilityReport.ProductRow> topProducts = params(TOP_PRODUCTS_SQL, storeId, range)
                .query((rs, row) -> new VisibilityReport.ProductRow(rs.getObject("product_id", UUID.class),
                        rs.getString("name"), rs.getLong("views"), rs.getLong("visitors"), rs.getLong("shares")))
                .list();
        List<VisibilityReport.DayRow> daily = params(DAILY_SQL, storeId, range)
                .query((rs, row) -> new VisibilityReport.DayRow(rs.getObject("day", LocalDate.class),
                        rs.getLong("profile_views"), rs.getLong("product_views")))
                .list();

        return new VisibilityReport(range.from(), range.to(), profile.views(), profile.visitors(), product.views(),
                product.visitors(), shares, bySource, topProducts, daily);
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
