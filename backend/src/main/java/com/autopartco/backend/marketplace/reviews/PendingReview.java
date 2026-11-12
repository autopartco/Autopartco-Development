package com.autopartco.backend.marketplace.reviews;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Algo por calificar de un pedido entregado: el almacén y sus productos recibidos. Un pedido con ventas de varios
 * almacenes aparece una vez por almacén. {@code image}: miniatura de la imagen principal o {@code null}.
 */
public record PendingReview(
        UUID orderId,
        long orderNumber,
        OffsetDateTime deliveredAt,
        Store store,
        List<Product> products
) {
    public PendingReview {
        products = products == null ? List.of() : List.copyOf(products);
    }

    /** {@code reviewId}: la reseña vigente del almacén en este pedido, para editarla; {@code null} si no hay. */
    public record Store(UUID id, String name, boolean reviewed, UUID reviewId) {
    }

    /** {@code reviewId}: la reseña vigente del producto, para editarla; {@code null} si no hay. */
    public record Product(UUID productId, String name, String image, boolean reviewed, UUID reviewId) {
    }
}
