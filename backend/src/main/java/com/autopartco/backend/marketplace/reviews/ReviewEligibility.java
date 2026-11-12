package com.autopartco.backend.marketplace.reviews;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Pedidos entregados del comprador con este almacén que todavía no tienen reseña ({@code eligibleOrderIds}) y, si ya
 * calificó alguno, la reseña vigente del pedido entregado más reciente ({@code existingReviewId}) con su fecha límite
 * de edición ({@code editableUntil}, creación + 30 días); sin reseña, ambos {@code null}.
 */
public record ReviewEligibility(List<UUID> eligibleOrderIds, UUID existingReviewId, OffsetDateTime editableUntil) {
    public ReviewEligibility {
        eligibleOrderIds = eligibleOrderIds == null ? List.of() : List.copyOf(eligibleOrderIds);
    }

    static ReviewEligibility none() {
        return new ReviewEligibility(List.of(), null, null);
    }
}
