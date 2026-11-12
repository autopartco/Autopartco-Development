package com.autopartco.backend.marketplace.reviews;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import lombok.AllArgsConstructor;

/** Lecturas de lo pendiente por calificar de un comprador (pedidos propios con ventas entregadas). */
@Repository
@AllArgsConstructor
class PendingReviewQueries {
    static final int WINDOW_DAYS = 90;
    static final int MAX_ENTRIES = 50;

    /**
     * Pares pedido–almacén con alguna venta entregada en los últimos {@value #WINDOW_DAYS} días. La fecha de entrega es
     * la del envío entregado o, sin envío, la última actualización de la venta (cuando pasó a {@code DELIVERED}).
     */
    static final String ORDER_STORES_SQL = """
            SELECT s.order_id, o.order_number, b.store_id, st.business_name AS store_name,
                   max(coalesce(sh.delivered_at, s.updated_at))::timestamptz AS delivered_at,
                   EXISTS (SELECT 1 FROM store_reviews r
                           WHERE r.store_id = b.store_id AND r.person_id = :personId AND r.order_id = s.order_id)
                       AS store_reviewed,
                   (SELECT r.id FROM store_reviews r
                    WHERE r.store_id = b.store_id AND r.person_id = :personId AND r.order_id = s.order_id
                      AND r.deleted_at IS NULL
                    ORDER BY r.created_at DESC, r.id LIMIT 1) AS store_review_id
            FROM sales s
            JOIN orders o ON o.id = s.order_id
            JOIN branches b ON b.id = s.branch_id
            JOIN store st ON st.id = b.store_id
            JOIN states ss ON ss.id = s.state_id
            LEFT JOIN LATERAL (
                SELECT max(shipment.delivered_at) AS delivered_at FROM shipments shipment WHERE shipment.sale_id = s.id
            ) sh ON true
            WHERE o.person_id = :personId AND o.deleted_at IS NULL AND s.deleted_at IS NULL AND ss.code = 'DELIVERED'
            GROUP BY s.order_id, o.order_number, b.store_id, st.business_name
            HAVING max(coalesce(sh.delivered_at, s.updated_at))::timestamptz >= now() - make_interval(days => :days)
            ORDER BY delivered_at DESC, s.order_id, b.store_id
            LIMIT :limit
            """;

    /** Productos recibidos en las ventas entregadas de esos pedidos, con su miniatura y si ya tienen reseña vigente. */
    static final String PRODUCTS_SQL = """
            SELECT DISTINCT ON (s.order_id, b.store_id, p.id)
                   s.order_id, b.store_id, p.id AS product_id, p.name, img.storage_path,
                   EXISTS (SELECT 1 FROM product_reviews r
                           WHERE r.person_id = :personId AND r.product_id = p.id AND r.deleted_at IS NULL)
                       AS reviewed,
                   (SELECT r.id FROM product_reviews r
                    WHERE r.person_id = :personId AND r.product_id = p.id AND r.deleted_at IS NULL
                    ORDER BY r.created_at DESC, r.id LIMIT 1) AS review_id
            FROM order_details d
            JOIN sales s ON s.id = d.sale_id
            JOIN branches b ON b.id = s.branch_id
            JOIN states ss ON ss.id = s.state_id
            LEFT JOIN inventory i ON i.id = d.inventory_id
            JOIN products p ON p.id = coalesce(d.product_id, i.product_id)
            LEFT JOIN LATERAL (
                SELECT pi.storage_path FROM product_images pi
                WHERE pi.product_id = p.id AND pi.deleted_at IS NULL
                ORDER BY pi.is_main DESC, pi.display_order, pi.id
                LIMIT 1
            ) img ON true
            WHERE s.order_id IN (:orderIds) AND d.deleted_at IS NULL AND s.deleted_at IS NULL
              AND ss.code = 'DELIVERED'
            ORDER BY s.order_id, b.store_id, p.id
            """;

    /** {@code storeReviewId}: reseña vigente del almacén en el pedido, o {@code null}. */
    record OrderStore(UUID orderId, long orderNumber, UUID storeId, String storeName, OffsetDateTime deliveredAt,
            boolean storeReviewed, UUID storeReviewId) {
    }

    /** {@code reviewId}: reseña vigente de la persona sobre el producto, o {@code null}. */
    record ProductLine(UUID orderId, UUID storeId, UUID productId, String name, String storagePath,
            boolean reviewed, UUID reviewId) {
    }

    private final JdbcClient jdbcClient;

    List<OrderStore> orderStores(UUID personId) {
        return jdbcClient.sql(ORDER_STORES_SQL)
                .param("personId", personId)
                .param("days", WINDOW_DAYS)
                .param("limit", MAX_ENTRIES)
                .query((rs, row) -> new OrderStore(rs.getObject("order_id", UUID.class), rs.getLong("order_number"),
                        rs.getObject("store_id", UUID.class), rs.getString("store_name"),
                        rs.getObject("delivered_at", OffsetDateTime.class), rs.getBoolean("store_reviewed"),
                        rs.getObject("store_review_id", UUID.class)))
                .list();
    }

    List<ProductLine> products(UUID personId, Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql(PRODUCTS_SQL)
                .param("personId", personId)
                .param("orderIds", List.copyOf(orderIds))
                .query((rs, row) -> new ProductLine(rs.getObject("order_id", UUID.class),
                        rs.getObject("store_id", UUID.class), rs.getObject("product_id", UUID.class),
                        rs.getString("name"), rs.getString("storage_path"), rs.getBoolean("reviewed"),
                        rs.getObject("review_id", UUID.class)))
                .list();
    }
}
