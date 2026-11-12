package com.autopartco.backend.core.reviews;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reseña vista desde el portal del almacén. {@code author} es nombre + inicial del apellido (nunca correo).
 * {@code hidden}: la ocultó la plataforma (no cuenta en promedios ni se muestra al público); el motivo no se expone.
 * {@code productId} y {@code productName} solo vienen en las de producto.
 */
public record PortalReview(
        UUID id,
        ReviewType type,
        int rating,
        String comment,
        Map<String, Integer> aspects,
        String author,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        ReviewReplyView reply,
        boolean hidden,
        UUID productId,
        String productName
) {
    public PortalReview {
        aspects = aspects == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(aspects));
    }
}
