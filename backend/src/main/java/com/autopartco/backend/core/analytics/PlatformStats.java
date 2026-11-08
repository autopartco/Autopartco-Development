package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

/**
 * Estadísticas de la plataforma para administración: tráfico ({@code traffic_events}), cuentas ({@code accounts},
 * {@code types_accounts}, {@code account_identities}), accesos ({@code login_attempts}) y comercio ({@code sales},
 * {@code seller_applications}, {@code seller_plans}). Solo agregados.
 */
@Service
@AllArgsConstructor
public class PlatformStats {
    static final int TOP = 10;

    private final JdbcClient jdbcClient;

    public record Count(String key, long count) {
    }

    public record Traffic(long visits, long uniqueVisitors, List<Map<String, Object>> series, List<Count> topPaths,
                          List<Count> referrers, Map<String, Long> devices) {
    }

    public record Accounts(long total, long created, List<Count> byType, BigDecimal verifiedRate, long locked,
                           List<Count> socialShare, List<Map<String, Object>> series) {
    }

    public record Access(long logins, long failedLogins, long dau, long wau, long mau, List<Map<String, Object>> series) {
    }

    public record Commerce(long orders, BigDecimal gmv, BigDecimal averageTicket, long activeStores,
                           long pendingApplications, List<Count> subscriptionsByPlan, BigDecimal mrr) {
    }

    public record Report(LocalDate from, LocalDate to, Granularity granularity, Traffic traffic, Accounts accounts,
                         Access access, Commerce commerce) {
    }

    @Transactional(readOnly = true)
    public Report report(DateRange range, Granularity granularity) {
        OffsetDateTime from = range.startInstant();
        OffsetDateTime to = range.endInstant();
        String bucket = bucket(granularity);
        return new Report(range.from(), range.to(), granularity,
                traffic(range, bucket), accounts(from, to, bucket), access(from, to, bucket), commerce(from, to));
    }

    private Traffic traffic(DateRange range, String bucket) {
        Map<String, Object> totals = jdbcClient.sql("""
                        SELECT count(*) AS visits, count(DISTINCT visitor_hash) AS visitors
                        FROM traffic_events WHERE day BETWEEN :from AND :to
                        """)
                .param("from", range.from()).param("to", range.to())
                .query().singleRow();
        List<Map<String, Object>> series = jdbcClient.sql("""
                        SELECT %s AS date, count(*) AS visits, count(DISTINCT visitor_hash) AS visitors
                        FROM traffic_events WHERE day BETWEEN :from AND :to
                        GROUP BY 1 ORDER BY 1
                        """.formatted(bucket.replace("%COL%", "day")))
                .param("from", range.from()).param("to", range.to())
                .query((result, row) -> point(result.getObject("date", LocalDate.class),
                        "visits", result.getLong("visits"), "visitors", result.getLong("visitors")))
                .list();
        List<Count> paths = counts("""
                SELECT path_group AS key, count(*) AS count FROM traffic_events
                WHERE day BETWEEN :from AND :to GROUP BY 1 ORDER BY 2 DESC, 1 LIMIT :top
                """, range);
        List<Count> referrers = counts("""
                SELECT referrer_host AS key, count(*) AS count FROM traffic_events
                WHERE day BETWEEN :from AND :to AND referrer_host IS NOT NULL GROUP BY 1 ORDER BY 2 DESC, 1 LIMIT :top
                """, range);
        Map<String, Long> devices = new LinkedHashMap<>();
        devices.put("mobile", 0L);
        devices.put("desktop", 0L);
        counts("""
                SELECT device AS key, count(*) AS count FROM traffic_events
                WHERE day BETWEEN :from AND :to GROUP BY 1 ORDER BY 1 LIMIT :top
                """, range).forEach(count -> devices.put(count.key(), count.count()));
        return new Traffic(number(totals.get("visits")), number(totals.get("visitors")), series, paths, referrers, devices);
    }

    private Accounts accounts(OffsetDateTime from, OffsetDateTime to, String bucket) {
        Map<String, Object> totals = jdbcClient.sql("""
                        SELECT count(*) AS total,
                               count(*) FILTER (WHERE a.created_at >= :from AND a.created_at < :to) AS created,
                               count(*) FILTER (WHERE s.code = 'ACTIVE') AS active,
                               count(*) FILTER (WHERE s.code = 'LOCKED') AS locked
                        FROM accounts a LEFT JOIN states s ON s.id = a.state_id
                        WHERE a.deleted_at IS NULL
                        """)
                .param("from", from).param("to", to)
                .query().singleRow();
        List<Count> byType = jdbcClient.sql("""
                        SELECT t.code AS key, count(*) AS count
                        FROM accounts a JOIN types_accounts t ON t.id = a.type_id
                        WHERE a.deleted_at IS NULL GROUP BY 1 ORDER BY 2 DESC
                        """)
                .query((result, row) -> new Count(result.getString("key"), result.getLong("count")))
                .list();
        List<Count> social = jdbcClient.sql("""
                        SELECT provider AS key, count(DISTINCT account_id) AS count
                        FROM account_identities WHERE deleted_at IS NULL GROUP BY 1 ORDER BY 2 DESC
                        """)
                .query((result, row) -> new Count(result.getString("key"), result.getLong("count")))
                .list();
        List<Map<String, Object>> series = jdbcClient.sql("""
                        SELECT %s AS date, count(*) AS created
                        FROM accounts a
                        WHERE a.deleted_at IS NULL AND a.created_at >= :from AND a.created_at < :to
                        GROUP BY 1 ORDER BY 1
                        """.formatted(bucket.replace("%COL%", localDay("a.created_at"))))
                .param("from", from).param("to", to)
                .query((result, row) -> point(result.getObject("date", LocalDate.class), "created", result.getLong("created"), null, 0))
                .list();
        long total = number(totals.get("total"));
        return new Accounts(total, number(totals.get("created")), byType,
                Analytics.ratio(BigDecimal.valueOf(number(totals.get("active"))), total),
                number(totals.get("locked")), social, series);
    }

    private Access access(OffsetDateTime from, OffsetDateTime to, String bucket) {
        Map<String, Object> totals = jdbcClient.sql("""
                        SELECT count(*) FILTER (WHERE successful) AS logins,
                               count(*) FILTER (WHERE NOT successful) AS failed
                        FROM login_attempts
                        WHERE deleted_at IS NULL AND attempted_at >= :from AND attempted_at < :to
                        """)
                .param("from", from).param("to", to)
                .query().singleRow();
        Map<String, Object> active = jdbcClient.sql("""
                        SELECT count(DISTINCT account_id) FILTER (WHERE attempted_at >= CAST(:to AS timestamptz) - interval '1 day') AS dau,
                               count(DISTINCT account_id) FILTER (WHERE attempted_at >= CAST(:to AS timestamptz) - interval '7 days') AS wau,
                               count(DISTINCT account_id) AS mau
                        FROM login_attempts
                        WHERE deleted_at IS NULL AND successful
                          AND attempted_at >= CAST(:to AS timestamptz) - interval '30 days' AND attempted_at < :to
                        """)
                .param("to", to)
                .query().singleRow();
        List<Map<String, Object>> series = jdbcClient.sql("""
                        SELECT %s AS date,
                               count(*) FILTER (WHERE successful) AS logins,
                               count(*) FILTER (WHERE NOT successful) AS failed
                        FROM login_attempts
                        WHERE deleted_at IS NULL AND attempted_at >= :from AND attempted_at < :to
                        GROUP BY 1 ORDER BY 1
                        """.formatted(bucket.replace("%COL%", localDay("attempted_at"))))
                .param("from", from).param("to", to)
                .query((result, row) -> point(result.getObject("date", LocalDate.class),
                        "logins", result.getLong("logins"), "failed", result.getLong("failed")))
                .list();
        return new Access(number(totals.get("logins")), number(totals.get("failed")),
                number(active.get("dau")), number(active.get("wau")), number(active.get("mau")), series);
    }

    private Commerce commerce(OffsetDateTime from, OffsetDateTime to) {
        Map<String, Object> sales = jdbcClient.sql("""
                        SELECT count(*) AS orders, coalesce(sum(s.total), 0) AS gmv
                        FROM sales s JOIN states st ON st.id = s.state_id
                        WHERE s.deleted_at IS NULL AND st.code IN %s
                          AND s.created_at >= :from AND s.created_at < :to
                        """.formatted(Analytics.VALID_SALE_STATES))
                .param("from", from).param("to", to)
                .query().singleRow();
        Map<String, Object> stores = jdbcClient.sql("""
                        SELECT count(*) FILTER (WHERE a.status = 'ACTIVE') AS active,
                               count(*) FILTER (WHERE a.status = 'PENDING_REVIEW') AS pending,
                               coalesce(sum(p.monthly_price_cop) FILTER (WHERE a.status = 'ACTIVE' AND a.paid_through > now()), 0) AS mrr
                        FROM seller_applications a LEFT JOIN seller_plans p ON p.id = a.plan_id
                        WHERE a.deleted_at IS NULL
                        """)
                .query().singleRow();
        List<Count> byPlan = jdbcClient.sql("""
                        SELECT coalesce(p.name, 'Sin plan') AS key, count(*) AS count
                        FROM seller_applications a LEFT JOIN seller_plans p ON p.id = a.plan_id
                        WHERE a.deleted_at IS NULL AND a.status = 'ACTIVE'
                        GROUP BY 1 ORDER BY 2 DESC
                        """)
                .query((result, row) -> new Count(result.getString("key"), result.getLong("count")))
                .list();
        long orders = number(sales.get("orders"));
        BigDecimal gmv = Analytics.money(decimal(sales.get("gmv")));
        return new Commerce(orders, gmv, orders == 0 ? BigDecimal.ZERO : Analytics.money(gmv.divide(BigDecimal.valueOf(orders), 2, java.math.RoundingMode.HALF_UP)),
                number(stores.get("active")), number(stores.get("pending")), byPlan, Analytics.money(decimal(stores.get("mrr"))));
    }

    private List<Count> counts(String sql, DateRange range) {
        return jdbcClient.sql(sql)
                .param("from", range.from()).param("to", range.to()).param("top", TOP)
                .query((result, row) -> new Count(result.getString("key"), result.getLong("count")))
                .list();
    }

    /** Expresión SQL del inicio del periodo; {@code %COL%} es una fecha local. */
    static String bucket(Granularity granularity) {
        return switch (granularity) {
            case DAY -> "CAST(%COL% AS date)";
            case WEEK -> "CAST(date_trunc('week', %COL%) AS date)";
            case MONTH -> "CAST(date_trunc('month', %COL%) AS date)";
        };
    }

    private static String localDay(String column) {
        return "CAST(" + column + " AT TIME ZONE 'America/Bogota' AS date)";
    }

    private static Map<String, Object> point(LocalDate date, String keyA, long a, String keyB, long b) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("date", date);
        point.put(keyA, a);
        if (keyB != null) {
            point.put(keyB, b);
        }
        return point;
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return value instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : BigDecimal.ZERO;
    }
}
