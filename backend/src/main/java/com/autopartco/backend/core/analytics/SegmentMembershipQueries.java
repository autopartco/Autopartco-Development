package com.autopartco.backend.core.analytics;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import lombok.AllArgsConstructor;

/**
 * Actividad de compra de UNA persona, por almacén y en toda la plataforma, en una sola consulta
 * ({@code GROUPING SETS}): mismas ventas válidas y mismos conteos que {@link CustomerAnalyticsQueries#ACTIVITY_SQL}.
 */
@Repository
@AllArgsConstructor
class SegmentMembershipQueries {
    static final String BUYER_ACTIVITY_SQL = """
            SELECT b.store_id,
                   GROUPING(b.store_id) = 1 AS platform_total,
                   count(*) FILTER (WHERE s.created_at >= :windowStart) AS orders,
                   min(s.created_at) AS first_order_at,
                   max(s.created_at) AS last_order_at
            FROM sales s
            JOIN branches b ON b.id = s.branch_id
            JOIN states st ON st.id = s.state_id
            JOIN orders o ON o.id = s.order_id
            WHERE s.deleted_at IS NULL
              AND st.code IN %s
              AND o.person_id = :personId
            GROUP BY GROUPING SETS ((b.store_id), ())
            """.formatted(Analytics.VALID_SALE_STATES);

    private final JdbcClient jdbcClient;

    /**
     * Fila por almacén ({@code storeId} no nulo) y una fila de toda la plataforma ({@code storeId} nulo). Sin compras,
     * la fila de plataforma llega con 0 compras y fechas nulas.
     */
    record Row(UUID storeId, long orders, OffsetDateTime firstOrderAt, OffsetDateTime lastOrderAt) {
    }

    List<Row> buyerActivity(UUID personId, OffsetDateTime windowStart) {
        return jdbcClient.sql(BUYER_ACTIVITY_SQL)
                .param("personId", personId)
                .param("windowStart", windowStart)
                .query((result, row) -> new Row(
                        result.getBoolean("platform_total") ? null : result.getObject("store_id", UUID.class),
                        result.getLong("orders"),
                        result.getObject("first_order_at", OffsetDateTime.class),
                        result.getObject("last_order_at", OffsetDateTime.class)))
                .list();
    }
}
