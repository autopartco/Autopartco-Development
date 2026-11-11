package com.autopartco.backend.marketplace.reviews;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import lombok.AllArgsConstructor;

/**
 * Elegibilidad y alta de {@code product_reviews}. El producto de una línea es {@code order_details.product_id} (o el
 * de su {@code inventory} en líneas antiguas sin él). No comprueba permisos.
 */
@Repository
@AllArgsConstructor
class ProductReviewQueries {

    /** Línea del producto en una venta entregada del pedido {@code o}. */
    private static final String DELIVERED_LINE = """
            SELECT 1
            FROM order_details d
            JOIN sales s ON s.id = d.sale_id
            JOIN states st ON st.id = s.state_id
            LEFT JOIN inventory i ON i.id = d.inventory_id
            WHERE d.order_id = o.id AND d.deleted_at IS NULL AND s.deleted_at IS NULL AND st.code = 'DELIVERED'
              AND coalesce(d.product_id, i.product_id) = :productId
            """;

    static final String PRODUCT_STORE_SQL = """
            SELECT store_id FROM products WHERE id = :productId AND deleted_at IS NULL
            """;

    static final String ELIGIBLE_ORDERS_SQL = """
            SELECT o.id
            FROM orders o
            WHERE o.person_id = :personId AND o.deleted_at IS NULL
              AND EXISTS (""" + DELIVERED_LINE + """
            )
            ORDER BY o.created_at DESC, o.id
            LIMIT 20
            """;

    static final String DELIVERED_SQL = """
            SELECT EXISTS (
                SELECT 1 FROM orders o
                WHERE o.id = :orderId AND o.person_id = :personId AND o.deleted_at IS NULL
                  AND EXISTS (""" + DELIVERED_LINE + """
            ))
            """;

    static final String EXISTING_SQL = """
            SELECT id, created_at::timestamptz AS created_at
            FROM product_reviews
            WHERE person_id = :personId AND product_id = :productId AND deleted_at IS NULL
            """;

    static final String INSERT_SQL = """
            INSERT INTO product_reviews (product_id, store_id, person_id, order_id, rating, comment)
            VALUES (:productId, :storeId, :personId, :orderId, :rating, :comment)
            RETURNING id
            """;

    record Existing(UUID id, OffsetDateTime createdAt) {
    }

    private final JdbcClient jdbcClient;

    /** Almacén del producto no borrado; vacío si no existe. */
    Optional<UUID> productStore(UUID productId) {
        return jdbcClient.sql(PRODUCT_STORE_SQL).param("productId", productId).query(UUID.class).optional();
    }

    /** Pedidos propios (más recientes primero, hasta 20) con el producto en una venta entregada. */
    List<UUID> eligibleOrders(UUID productId, UUID personId) {
        return jdbcClient.sql(ELIGIBLE_ORDERS_SQL)
                .param("productId", productId)
                .param("personId", personId)
                .query(UUID.class)
                .list();
    }

    /** El pedido es de la persona y tiene el producto en una venta entregada. */
    boolean deliveredInOrder(UUID orderId, UUID personId, UUID productId) {
        return Boolean.TRUE.equals(jdbcClient.sql(DELIVERED_SQL)
                .param("orderId", orderId)
                .param("personId", personId)
                .param("productId", productId)
                .query(Boolean.class)
                .single());
    }

    Optional<Existing> existing(UUID personId, UUID productId) {
        return jdbcClient.sql(EXISTING_SQL)
                .param("personId", personId)
                .param("productId", productId)
                .query((rs, row) -> new Existing(rs.getObject("id", UUID.class),
                        rs.getObject("created_at", OffsetDateTime.class)))
                .optional();
    }

    /** @throws org.springframework.dao.DuplicateKeyException si la persona ya tiene una reseña vigente del producto. */
    UUID insert(UUID productId, UUID storeId, UUID personId, UUID orderId, int rating, String comment) {
        return jdbcClient.sql(INSERT_SQL)
                .param("productId", productId)
                .param("storeId", storeId)
                .param("personId", personId)
                .param("orderId", orderId)
                .param("rating", rating)
                .param("comment", comment)
                .query(UUID.class)
                .single();
    }
}
