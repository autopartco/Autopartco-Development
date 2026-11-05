package com.autopartco.backend.storesuite.inventory;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.inventory.StockTransfers;
import com.autopartco.backend.core.inventory.StockTransfers.TransferCommand;
import com.autopartco.backend.core.inventory.StockTransfers.TransferLine;
import com.autopartco.backend.core.inventory.StockTransfers.TransferResult;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.shared.error.BadRequestException;

import lombok.AllArgsConstructor;

/**
 * Transferencia de existencias entre dos sucursales activas del almacén. Requiere {@code INVENTORY_TRANSFER} (antes de
 * leer o escribir nada). Una clave de idempotencia repetida devuelve la transferencia existente sin volver a validar.
 */
@Service
@AllArgsConstructor
public class TransferStock {
    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final BranchService branchService;
    private final StockTransfers stockTransfers;
    private final InventoryActor inventoryActor;

    /** Resultado del POST: {@code created = false} si la clave de idempotencia ya existía (200 en vez de 201). */
    public record Outcome(TransferResponse transfer, boolean created) {
    }

    @Transactional
    public Outcome execute(UUID storeId, String email, TransferRequest request) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.INVENTORY_TRANSFER);
        storeService.requireExists(storeId);
        String idempotencyKey = clean(request.idempotencyKey());
        var existing = stockTransfers.findIdByKey(storeId, idempotencyKey).flatMap(id -> stockTransfers.find(storeId, id));
        if (existing.isPresent()) {
            return new Outcome(TransferResponse.from(existing.get()), false);
        }
        if (request.fromBranchId().equals(request.toBranchId())) {
            throw new BadRequestException("SAME_BRANCH", "La sucursal de origen y la de destino deben ser distintas.");
        }
        branchService.requireActiveInStore(storeId, request.fromBranchId());
        branchService.requireActiveInStore(storeId, request.toBranchId());
        UUID actorAccountId = inventoryActor.bind(email);

        List<TransferLine> lines = request.items() == null ? List.of() : request.items().stream()
                .map(item -> new TransferLine(item.productId(), item.quantity()))
                .toList();
        TransferResult result = stockTransfers.transfer(actorAccountId, new TransferCommand(storeId,
                request.fromBranchId(), request.toBranchId(), request.mode(), lines, clean(request.notes()),
                idempotencyKey));
        return new Outcome(TransferResponse.from(result.transfer()), result.created());
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
