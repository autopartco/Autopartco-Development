package com.autopartco.backend.marketplace.reviews;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Reseña pública de almacén. {@code author} (y {@code authorName}, que se conserva por compatibilidad) es nombre +
 * inicial del apellido («Ana P.»); nunca el nombre completo ni el correo. {@code verified} siempre es {@code true}.
 * {@code aspects}: código de {@link StoreAspect} → 1–5. {@code reply}: respuesta del almacén o {@code null}.
 */
public record StoreReviewResponse(
        UUID id,
        int rating,
        String comment,
        Map<String, Integer> aspects,
        String author,
        String authorName,
        boolean verified,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        ReviewReply reply
) {
    public StoreReviewResponse {
        aspects = aspects == null ? Map.of() : java.util.Collections.unmodifiableMap(
                new java.util.LinkedHashMap<>(aspects));
    }

    static StoreReviewResponse of(ReviewQueries.ReviewRow row, Map<String, Integer> aspects) {
        String author = StoreReviewService.authorName(row.name(), row.surname());
        return new StoreReviewResponse(row.id(), row.rating(), row.comment(), aspects, author, author, true,
                row.createdAt(), row.updatedAt(), ReviewReply.of(row.reply(), row.repliedAt()));
    }
}
