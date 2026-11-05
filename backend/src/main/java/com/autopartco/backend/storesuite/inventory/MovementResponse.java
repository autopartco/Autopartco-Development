package com.autopartco.backend.storesuite.inventory;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.autopartco.backend.core.inventory.InventoryQueries.MovementView;

/** Movimiento de inventario para el portal. {@code actorName} es nulo si el actor ya no existe o fue el sistema. */
public record MovementResponse(
        UUID id,
        OffsetDateTime occurredAt,
        UUID branchId,
        String branchName,
        UUID productId,
        String productName,
        String sku,
        String movementType,
        int quantity,
        int previousStock,
        int newStock,
        String reasonCode,
        String reasonLabel,
        String actorName,
        String referenceType,
        UUID referenceId,
        String notes
) {
    static MovementResponse from(MovementView view) {
        return new MovementResponse(view.id(), view.occurredAt(), view.branchId(), view.branchName(),
                view.productId(), view.productName(), view.sku(), view.movementType(), view.quantity(),
                view.previousStock(), view.newStock(), view.reasonCode(), view.reasonLabel(), view.actorName(),
                view.referenceType(), view.referenceId(), view.notes());
    }
}
