package com.autopartco.backend.storesuite.inventory;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.autopartco.backend.core.inventory.StockTransfers.TransferSummaryView;

/** Fila del listado de transferencias. */
public record TransferSummary(
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
        OffsetDateTime createdAt
) {
    static TransferSummary from(TransferSummaryView view) {
        return new TransferSummary(view.id(), view.fromBranchId(), view.fromBranchName(), view.toBranchId(),
                view.toBranchName(), view.mode(), view.totalUnits(), view.itemCount(), view.notes(),
                view.actorName(), view.createdAt());
    }
}
