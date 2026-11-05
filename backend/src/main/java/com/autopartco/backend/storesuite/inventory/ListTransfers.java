package com.autopartco.backend.storesuite.inventory;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.inventory.StockTransfers;
import com.autopartco.backend.core.inventory.StockTransfers.TransferSummaryView;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/** Listado y detalle de transferencias del almacén. Requiere {@code PRODUCT_READ}. */
@Service
@AllArgsConstructor
public class ListTransfers {
    public static final int MAX_PAGE_SIZE = 100;

    private final StoreAccess storeAccess;
    private final StockTransfers stockTransfers;

    /** {@code page} desde 1; {@code size} entre 1 y {@value #MAX_PAGE_SIZE}; {@code branchId}: origen o destino. */
    @Transactional(readOnly = true)
    public PageResponse<TransferSummary> list(UUID storeId, String email, UUID branchId, int page, int size) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_READ);
        if (page < 1) {
            throw new BadRequestException("VALIDATION_ERROR", "La página debe ser mayor o igual a 1.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("VALIDATION_ERROR",
                    "El tamaño de página debe estar entre 1 y " + MAX_PAGE_SIZE + ".");
        }
        PageResponse<TransferSummaryView> result = stockTransfers.list(storeId, branchId, page, size);
        return new PageResponse<>(result.items().stream().map(TransferSummary::from).toList(),
                result.page(), result.size(), result.total(), result.totalPages());
    }

    /** Transferencia de otro almacén o inexistente: {@code 404 TRANSFER_NOT_FOUND}. */
    @Transactional(readOnly = true)
    public TransferResponse get(UUID storeId, String email, UUID transferId) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_READ);
        return stockTransfers.find(storeId, transferId)
                .map(TransferResponse::from)
                .orElseThrow(() -> new NotFoundException("TRANSFER_NOT_FOUND", "No se encontró la transferencia."));
    }
}
