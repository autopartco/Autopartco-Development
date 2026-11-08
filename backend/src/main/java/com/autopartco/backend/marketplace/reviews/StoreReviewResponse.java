package com.autopartco.backend.marketplace.reviews;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Reseña pública. {@code authorName} es nombre + inicial del apellido («Ana P.»); nunca el nombre completo. */
public record StoreReviewResponse(
        UUID id,
        int rating,
        String comment,
        String authorName,
        OffsetDateTime createdAt
) {
}
