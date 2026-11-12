package com.autopartco.backend.core.reviews;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Reseña vista por administración. {@code author} es nombre + inicial del apellido (nunca correo). Las de producto
 * traen {@code productId} y {@code productName}; todas, el almacén. {@code hiddenAt} y {@code hiddenReason} solo si
 * está oculta.
 */
public record AdminReview(
        UUID id,
        ReviewType type,
        int rating,
        String comment,
        String author,
        OffsetDateTime createdAt,
        ReviewReplyView reply,
        UUID storeId,
        String storeName,
        UUID productId,
        String productName,
        boolean hidden,
        OffsetDateTime hiddenAt,
        String hiddenReason
) {
}
