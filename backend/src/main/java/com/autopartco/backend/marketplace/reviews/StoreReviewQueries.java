package com.autopartco.backend.marketplace.reviews;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import lombok.AllArgsConstructor;

/**
 * Elegibilidad, alta y promedio de {@code store_reviews}. El listado, el resumen, la edición y el borrado son comunes
 * con las de producto ({@link ReviewQueries}). No comprueba permisos ni publicación.
 */
@Repository
@AllArgsConstructor
class StoreReviewQueries {

    /** Venta entregada del pedido en alguna sucursal del almacén. */
    private static final String DELIVERED_SALE_OF_STORE = """
            SELECT 1
            FROM sales s
            JOIN branches b ON b.id = s.branch_id
            JOIN states st ON st.id = s.state_id
            WHERE s.order_id = o.id AND b.store_id = :storeId AND s.deleted_at IS NULL AND st.code = 'DELIVERED'
            """;

    /** Promedio y conteo de las reseñas visibles (sin borradas ni ocultas). */
    static final String RATING_SQL = """
            SELECT avg(rating) AS average, count(*) AS total
            FROM store_reviews
            WHERE store_id = :storeId AND deleted_at IS NULL AND hidden_at IS NULL
            """;

    static final String ORDER_OWNER_SQL = """
            SELECT person_id FROM orders WHERE id = :orderId AND deleted_at IS NULL
            """;

    static final String DELIVERED_SQL = "SELECT EXISTS (SELECT 1 FROM orders o WHERE o.id = :orderId AND EXISTS ("
            + DELIVERED_SALE_OF_STORE + "))";

    static final String ELIGIBLE_ORDERS_SQL = """
            SELECT o.id
            FROM orders o
            WHERE o.person_id = :personId AND o.deleted_at IS NULL
              AND EXISTS (""" + DELIVERED_SALE_OF_STORE + """
            )
              AND NOT EXISTS (
                  SELECT 1 FROM store_reviews r
                  WHERE r.store_id = :storeId AND r.person_id = :personId AND r.order_id = o.id
                    AND (r.deleted_at IS NULL OR r.hidden_at IS NOT NULL)
              )
            ORDER BY o.created_at DESC
            LIMIT 20
            """;

    static final String INSERT_SQL = """
            INSERT INTO store_reviews (store_id, person_id, order_id, rating, comment)
            VALUES (:storeId, :personId, :orderId, :rating, :comment)
            RETURNING id
            """;

    /**
     * Reseña vigente (no borrada, aunque esté oculta) de la persona sobre el almacén en su pedido entregado más
     * reciente que ya calificó.
     */
    static final String LATEST_REVIEWED_SQL = """
            SELECT r.id, r.created_at::timestamptz AS created_at
            FROM store_reviews r
            JOIN orders o ON o.id = r.order_id AND o.deleted_at IS NULL
            WHERE r.store_id = :storeId AND r.person_id = :personId AND r.deleted_at IS NULL
              AND EXISTS (""" + DELIVERED_SALE_OF_STORE + """
            )
            ORDER BY o.created_at DESC, r.created_at DESC, r.id
            LIMIT 1
            """;

    static final String REVIEW_OF_ORDER_SQL = """
            SELECT r.id
            FROM store_reviews r
            WHERE r.store_id = :storeId AND r.person_id = :personId AND r.order_id = :orderId
              AND r.deleted_at IS NULL
            """;

    /** Reseña borrada de la persona sobre el almacén en ese pedido (el índice único no es parcial: hay una a lo sumo). */
    static final String DELETED_SQL = """
            SELECT id, hidden_at IS NOT NULL AS hidden
            FROM store_reviews
            WHERE store_id = :storeId AND person_id = :personId AND order_id = :orderId AND deleted_at IS NOT NULL
            """;

    record Reviewed(UUID id, OffsetDateTime createdAt) {
    }

    private final JdbcClient jdbcClient;

    Optional<Reviewed> latestReviewed(UUID storeId, UUID personId) {
        return jdbcClient.sql(LATEST_REVIEWED_SQL)
                .param("storeId", storeId)
                .param("personId", personId)
                .query((rs, row) -> new Reviewed(rs.getObject("id", UUID.class),
                        rs.getObject("created_at", OffsetDateTime.class)))
                .optional();
    }

    /** Reseña vigente de la persona sobre el almacén en ese pedido. */
    Optional<UUID> reviewOfOrder(UUID storeId, UUID personId, UUID orderId) {
        return jdbcClient.sql(REVIEW_OF_ORDER_SQL)
                .param("storeId", storeId)
                .param("personId", personId)
                .param("orderId", orderId)
                .query(UUID.class)
                .optional();
    }

    StoreRating rating(UUID storeId) {
        return jdbcClient.sql(RATING_SQL)
                .param("storeId", storeId)
                .query((rs, row) -> {
                    long total = rs.getLong("total");
                    BigDecimal average = rs.getBigDecimal("average");
                    return total == 0 || average == null
                            ? StoreRating.NONE
                            : new StoreRating(average.setScale(1, RoundingMode.HALF_UP), total);
                })
                .single();
    }

    Optional<UUID> orderOwner(UUID orderId) {
        return jdbcClient.sql(ORDER_OWNER_SQL).param("orderId", orderId).query(UUID.class).optional();
    }

    boolean hasDeliveredSale(UUID orderId, UUID storeId) {
        return Boolean.TRUE.equals(jdbcClient.sql(DELIVERED_SQL)
                .param("orderId", orderId)
                .param("storeId", storeId)
                .query(Boolean.class)
                .single());
    }

    List<UUID> eligibleOrders(UUID storeId, UUID personId) {
        return jdbcClient.sql(ELIGIBLE_ORDERS_SQL)
                .param("storeId", storeId)
                .param("personId", personId)
                .query(UUID.class)
                .list();
    }

    Optional<ReviewQueries.Deleted> deleted(UUID storeId, UUID personId, UUID orderId) {
        return jdbcClient.sql(DELETED_SQL)
                .param("storeId", storeId)
                .param("personId", personId)
                .param("orderId", orderId)
                .query((rs, row) -> new ReviewQueries.Deleted(rs.getObject("id", UUID.class),
                        rs.getBoolean("hidden")))
                .optional();
    }

    /** @throws org.springframework.dao.DuplicateKeyException si ya hay reseña del pedido para el almacén. */
    UUID insert(UUID storeId, UUID personId, UUID orderId, int rating, String comment) {
        return jdbcClient.sql(INSERT_SQL)
                .param("storeId", storeId)
                .param("personId", personId)
                .param("orderId", orderId)
                .param("rating", rating)
                .param("comment", comment)
                .query(UUID.class)
                .single();
    }
}
