package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import lombok.AllArgsConstructor;

/**
 * Lecturas agregadas de los resultados de una promoción ({@code promotion_exposures}, {@code order_details},
 * {@code sales}, {@code orders}, {@code branches}, {@code states}). Solo devuelve conteos y sumas. Almacén opcional:
 * sin él, el ámbito es toda la plataforma.
 */
@Repository
@AllArgsConstructor
class PromotionResultQueries {
    private static final String LINE_REVENUE = "(d.units * d.unit_price - coalesce(d.discount_amount, 0))";

    /** Ventas de la promoción: personas, pedidos, ingresos y unidades de sus líneas en ventas válidas. */
    static final String ATTRIBUTED_SQL = """
            SELECT count(DISTINCT o.person_id) AS buyers,
                   count(DISTINCT s.order_id) AS orders,
                   coalesce(sum(%s), 0) AS revenue,
                   coalesce(sum(d.units), 0) AS units
            FROM order_details d
            JOIN sales s ON s.id = d.sale_id
            JOIN states st ON st.id = s.state_id
            JOIN orders o ON o.id = s.order_id
            WHERE d.promotion_id = :promotionId
              AND s.deleted_at IS NULL
              AND st.code IN %s
            """.formatted(LINE_REVENUE, Analytics.VALID_SALE_STATES);

    /**
     * Expuestos y grupo de control. {@code scope_buyers} son las personas con venta válida del ámbito dentro de la
     * vigencia; {@code prior_buyers} las del ámbito en los 365 días previos al inicio.
     */
    static final String CONVERSION_SQL = """
            WITH exposed AS (
                SELECT DISTINCT person_id FROM promotion_exposures
                WHERE promotion_id = :promotionId AND person_id IS NOT NULL
            ),
            scope_buyers AS (
                SELECT DISTINCT o.person_id
                FROM sales s
                JOIN branches b ON b.id = s.branch_id
                JOIN states st ON st.id = s.state_id
                JOIN orders o ON o.id = s.order_id
                WHERE s.deleted_at IS NULL
                  AND st.code IN %1$s
                  AND s.created_at >= :startsAt AND s.created_at < :windowEnd
                  AND (CAST(:storeId AS uuid) IS NULL OR b.store_id = CAST(:storeId AS uuid))
            ),
            prior_buyers AS (
                SELECT DISTINCT o.person_id
                FROM sales s
                JOIN branches b ON b.id = s.branch_id
                JOIN states st ON st.id = s.state_id
                JOIN orders o ON o.id = s.order_id
                WHERE s.deleted_at IS NULL
                  AND st.code IN %1$s
                  AND s.created_at >= :priorStart AND s.created_at < :startsAt
                  AND (CAST(:storeId AS uuid) IS NULL OR b.store_id = CAST(:storeId AS uuid))
            )
            SELECT (SELECT count(*) FROM exposed) AS exposed,
                   (SELECT count(*) FROM exposed e
                    WHERE EXISTS (SELECT 1 FROM scope_buyers sb WHERE sb.person_id = e.person_id)) AS exposed_buyers,
                   (SELECT count(*) FROM prior_buyers pb
                    WHERE NOT EXISTS (SELECT 1 FROM exposed e WHERE e.person_id = pb.person_id)) AS control_size,
                   (SELECT count(*) FROM prior_buyers pb
                    WHERE NOT EXISTS (SELECT 1 FROM exposed e WHERE e.person_id = pb.person_id)
                      AND EXISTS (SELECT 1 FROM scope_buyers sb WHERE sb.person_id = pb.person_id)) AS control_buyers
            """.formatted(Analytics.VALID_SALE_STATES);

    /** Ingresos y pedidos del ámbito en la ventana previa (línea base). */
    static final String BASELINE_SQL = """
            SELECT count(DISTINCT s.order_id) AS orders,
                   coalesce(sum(%s), 0) AS revenue
            FROM order_details d
            JOIN sales s ON s.id = d.sale_id
            JOIN branches b ON b.id = s.branch_id
            JOIN states st ON st.id = s.state_id
            WHERE s.deleted_at IS NULL
              AND st.code IN %s
              AND s.created_at >= :baselineStart AND s.created_at < :startsAt
              AND (CAST(:storeId AS uuid) IS NULL OR b.store_id = CAST(:storeId AS uuid))
            """.formatted(LINE_REVENUE, Analytics.VALID_SALE_STATES);

    private final JdbcClient jdbcClient;

    record Attributed(long buyers, long orders, BigDecimal revenue, long units) {
    }

    record Conversion(long exposed, long exposedBuyers, long controlSize, long controlBuyers) {
    }

    record BaselineTotals(long orders, BigDecimal revenue) {
    }

    Attributed attributed(UUID promotionId) {
        return jdbcClient.sql(ATTRIBUTED_SQL)
                .param("promotionId", promotionId)
                .query((rs, row) -> new Attributed(
                        rs.getLong("buyers"), rs.getLong("orders"), rs.getBigDecimal("revenue"), rs.getLong("units")))
                .single();
    }

    Conversion conversion(UUID promotionId, UUID storeId, OffsetDateTime startsAt, OffsetDateTime windowEnd,
            OffsetDateTime priorStart) {
        return jdbcClient.sql(CONVERSION_SQL)
                .param("promotionId", promotionId)
                .param("storeId", storeId)
                .param("startsAt", startsAt)
                .param("windowEnd", windowEnd)
                .param("priorStart", priorStart)
                .query((rs, row) -> new Conversion(
                        rs.getLong("exposed"), rs.getLong("exposed_buyers"),
                        rs.getLong("control_size"), rs.getLong("control_buyers")))
                .single();
    }

    BaselineTotals baseline(UUID storeId, OffsetDateTime startsAt, OffsetDateTime baselineStart) {
        return jdbcClient.sql(BASELINE_SQL)
                .param("storeId", storeId)
                .param("startsAt", startsAt)
                .param("baselineStart", baselineStart)
                .query((rs, row) -> new BaselineTotals(rs.getLong("orders"), rs.getBigDecimal("revenue")))
                .single();
    }
}
