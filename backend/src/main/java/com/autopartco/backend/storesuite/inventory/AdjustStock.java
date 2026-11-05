package com.autopartco.backend.storesuite.inventory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.inventory.InventoryQueries;
import com.autopartco.backend.core.inventory.InventoryQueries.ReasonView;
import com.autopartco.backend.core.inventory.StockLedger;
import com.autopartco.backend.core.inventory.StockLedger.LockedListing;
import com.autopartco.backend.core.inventory.StockLedger.MovementRow;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/**
 * Ajuste de existencias con motivo. Requiere {@code INVENTORY_ADJUST} (antes de leer o escribir nada). Bloquea el
 * listado, valida cantidad y dirección del motivo sobre el stock bloqueado y escribe el movimiento por el ledger.
 */
@Service
@AllArgsConstructor
public class AdjustStock {
    /** Motivos que el portal puede usar en un ajuste. */
    public static final Set<String> ADJUSTABLE_REASONS = Set.of(
            "COUNT_ADJUSTMENT", "MANUAL_CORRECTION", "DAMAGE", "LOSS_THEFT", "OBSOLESCENCE");

    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final BranchService branchService;
    private final InventoryQueries inventoryQueries;
    private final StockLedger stockLedger;
    private final InventoryActor inventoryActor;

    @Transactional
    public MovementResponse execute(UUID storeId, String email, StockAdjustmentRequest request) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.INVENTORY_ADJUST);
        storeService.requireExists(storeId);
        String reasonCode = request.reasonCode().trim();
        if (!ADJUSTABLE_REASONS.contains(reasonCode)) {
            throw invalidReason();
        }
        if (request.isSet() ? request.quantity() < 0 : request.quantity() == 0) {
            throw new BadRequestException("INVALID_QUANTITY", request.isSet()
                    ? "La nueva existencia no puede ser negativa."
                    : "La cantidad a ajustar no puede ser 0.");
        }
        branchService.requireActiveInStore(storeId, request.branchId());
        UUID inventoryId = inventoryQueries.listing(storeId, request.branchId(), request.productId())
                .orElseThrow(() -> new NotFoundException(
                        "LISTING_NOT_FOUND", "El producto no está listado en esa sucursal."))
                .inventoryId();
        ReasonView reason = inventoryQueries.reasons(List.of(reasonCode)).stream()
                .findFirst()
                .orElseThrow(AdjustStock::invalidReason);
        UUID actorAccountId = inventoryActor.bind(email);

        LockedListing locked = stockLedger.lock(List.of(inventoryId)).getFirst();
        long target = request.isSet() ? request.quantity() : (long) locked.stock() + request.quantity();
        if (target < 0) {
            throw StockLedger.insufficientStock(List.of(Map.of(
                    "productId", request.productId(),
                    "requested", -request.quantity(),
                    "available", locked.stock())));
        }
        if (target > Integer.MAX_VALUE) {
            throw new BadRequestException("INVALID_QUANTITY", "La existencia resultante es demasiado grande.");
        }
        int newStock = (int) target;
        int delta = newStock - locked.stock();
        if (delta == 0) {
            throw new BadRequestException("NO_CHANGES", "La existencia ya es " + newStock + ".");
        }
        String direction = delta > 0 ? "IN" : "OUT";
        if (!"ANY".equals(reason.direction()) && !reason.direction().equals(direction)) {
            throw new BadRequestException("REASON_DIRECTION", "El motivo no corresponde a "
                    + ("IN".equals(direction) ? "una entrada" : "una salida") + " de existencias.");
        }

        List<MovementRow> written = stockLedger.apply(actorAccountId, StockLedger.SOURCE_USER, List.of(
                StockLedger.StockChange.of(inventoryId, newStock, reasonCode, clean(request.notes()))));
        UUID movementId = written.getFirst().id();
        return inventoryQueries.movement(storeId, movementId)
                .map(MovementResponse::from)
                .orElseThrow(() -> new IllegalStateException("movement not readable: " + movementId));
    }

    private static BadRequestException invalidReason() {
        return new BadRequestException("INVALID_REASON", "El motivo de ajuste no es válido.");
    }

    private static String clean(String notes) {
        return notes == null || notes.isBlank() ? null : notes.trim();
    }
}
