package com.autopartco.backend.marketplace.reviews;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import lombok.AllArgsConstructor;

/** Lecturas y escrituras JDBC de {@code store_reviews}. No comprueba permisos ni publicación. */
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

    static final String LIST_SQL = """
            SELECT r.id, r.rating, r.comment, r.created_at, p.name, p.surname
            FROM store_reviews r
            JOIN persons p ON p.id = r.person_id
            WHERE r.store_id = :storeId AND r.deleted_at IS NULL
            ORDER BY r.created_at DESC, r.id
            LIMIT :limit OFFSET :offset
            """;

    static final String RATING_SQL = """
            SELECT avg(rating) AS average, count(*) AS total
            FROM store_reviews
            WHERE store_id = :storeId AND deleted_at IS NULL
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
              )
            ORDER BY o.created_at DESC
            LIMIT 20
            """;

    static final String INSERT_SQL = """
            INSERT INTO store_reviews (store_id, person_id, order_id, rating, comment)
            VALUES (:storeId, :personId, :orderId, :rating, :comment)
            RETURNING id, created_at
            """;

    private final JdbcClient jdbcClient;

    List<StoreReviewResponse> list(UUID storeId, int limit, int offset) {
        return jdbcClient.sql(LIST_SQL)
                .param("storeId", storeId)
                .param("limit", limit)
                .param("offset", offset)
                .query(StoreReviewQueries::mapReview)
                .list();
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

    /** @throws org.springframework.dao.DuplicateKeyException si ya hay reseña del pedido para el almacén. */
    Inserted insert(UUID storeId, UUID personId, UUID orderId, int rating, String comment) {
        return jdbcClient.sql(INSERT_SQL)
                .param("storeId", storeId)
                .param("personId", personId)
                .param("orderId", orderId)
                .param("rating", rating)
                .param("comment", comment)
                .query((rs, row) -> new Inserted(
                        rs.getObject("id", UUID.class), rs.getObject("created_at", OffsetDateTime.class)))
                .single();
    }

    record Inserted(UUID id, OffsetDateTime createdAt) {
    }

    private static StoreReviewResponse mapReview(ResultSet rs, int row) throws SQLException {
        return new StoreReviewResponse(
                rs.getObject("id", UUID.class),
                rs.getInt("rating"),
                rs.getString("comment"),
                StoreReviewService.authorName(rs.getString("name"), rs.getString("surname")),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }
}
