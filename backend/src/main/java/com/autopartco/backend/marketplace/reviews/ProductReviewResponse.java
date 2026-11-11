package com.autopartco.backend.marketplace.reviews;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Reseña de producto. {@code author} es nombre + inicial del apellido («Ana P.»), nunca el apellido completo ni el
 * correo; {@code verified} siempre es {@code true} (solo califica quien recibió el producto). {@code aspects}: código
 * de {@link ProductAspect} → 1–5. {@code reply}: respuesta del almacén o {@code null}.
 */
public record ProductReviewResponse(
        UUID id,
        int rating,
        String comment,
        Map<String, Integer> aspects,
        String author,
        boolean verified,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        ReviewReply reply
) {
    public ProductReviewResponse {
        aspects = aspects == null ? Map.of() : java.util.Collections.unmodifiableMap(
                new java.util.LinkedHashMap<>(aspects));
    }

    static ProductReviewResponse of(ReviewQueries.ReviewRow row, Map<String, Integer> aspects) {
        return new ProductReviewResponse(row.id(), row.rating(), row.comment(), aspects,
                StoreReviewService.authorName(row.name(), row.surname()), true, row.createdAt(), row.updatedAt(),
                ReviewReply.of(row.reply(), row.repliedAt()));
    }
}
