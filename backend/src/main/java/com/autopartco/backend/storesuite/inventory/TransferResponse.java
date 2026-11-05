package com.autopartco.backend.storesuite.inventory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.autopartco.backend.core.inventory.StockTransfers.TransferItemView;
import com.autopartco.backend.core.inventory.StockTransfers.TransferView;

/**
 * Transferencia con sus ítems. {@code fromStock}/{@code toStock}: existencias de origen y destino tras la
 * transferencia. {@code actorName} es nulo si la cuenta ya no existe.
 */
public record TransferResponse(
        UUID id,
        UUID fromBranchId,
        String fromBranchName,
        UUID toBranchId,
        String toBranchName,
        String mode,
        int totalUnits,
        int itemCount,
        String notes,
        String actorName,
        OffsetDateTime createdAt,
        List<Item> items
) {
    public record Item(UUID productId, String productName, int quantity, int fromStock, int toStock) {
        static Item from(TransferItemView view) {
            return new Item(view.productId(), view.productName(), view.quantity(), view.fromStock(), view.toStock());
        }
    }

    static TransferResponse from(TransferView view) {
        return new TransferResponse(view.id(), view.fromBranchId(), view.fromBranchName(), view.toBranchId(),
                view.toBranchName(), view.mode(), view.totalUnits(), view.itemCount(), view.notes(),
                view.actorName(), view.createdAt(), view.items().stream().map(Item::from).toList());
    }
}
