package com.autopartco.backend.marketplace.reviews;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Qué puede hacer el comprador con un producto. Sin reseña: {@code eligibleOrderIds} son sus pedidos con el producto
 * entregado (vacío = no puede calificar). Con reseña: {@code eligibleOrderIds} vacío, {@code existingReviewId} y
 * {@code editableUntil} (creación + 30 días).
 */
public record ProductReviewEligibility(List<UUID> eligibleOrderIds, UUID existingReviewId,
        OffsetDateTime editableUntil) {
    public ProductReviewEligibility {
        eligibleOrderIds = eligibleOrderIds == null ? List.of() : List.copyOf(eligibleOrderIds);
    }
}
