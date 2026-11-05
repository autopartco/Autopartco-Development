package com.autopartco.backend.storesuite.inventory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.inventory.InventoryQueries;
import com.autopartco.backend.core.inventory.InventoryQueries.MovementFilter;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/** Historial de movimientos y motivos de ajuste del almacén. Requiere {@code PRODUCT_READ}. */
@Service
@AllArgsConstructor
public class ListMovements {
    public static final int MAX_PAGE_SIZE = 100;

    private final StoreAccess storeAccess;
    private final InventoryQueries inventoryQueries;

    /** {@code page} desde 1; {@code size} entre 1 y {@value #MAX_PAGE_SIZE}; {@code from} incluido, {@code to} no. */
    @Transactional(readOnly = true)
    public PageResponse<MovementResponse> execute(
            UUID storeId,
            String email,
            UUID branchId,
            UUID productId,
            String reasonCode,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int size
    ) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_READ);
        if (page < 1) {
            throw new BadRequestException("VALIDATION_ERROR", "La página debe ser mayor o igual a 1.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("VALIDATION_ERROR",
                    "El tamaño de página debe estar entre 1 y " + MAX_PAGE_SIZE + ".");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BadRequestException("INVALID_DATE_RANGE", "La fecha inicial debe ser anterior a la final.");
        }
        String reason = reasonCode == null || reasonCode.isBlank() ? null : reasonCode.trim();
        PageResponse<InventoryQueries.MovementView> result = inventoryQueries.movements(storeId,
                new MovementFilter(branchId, productId, reason, from, to), page, size);
        return new PageResponse<>(result.items().stream().map(MovementResponse::from).toList(),
                result.page(), result.size(), result.total(), result.totalPages());
    }

    /** Motivos que admite un ajuste manual. */
    @Transactional(readOnly = true)
    public List<MovementReasonResponse> reasons(UUID storeId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_READ);
        return inventoryQueries.reasons(AdjustStock.ADJUSTABLE_REASONS).stream()
                .map(reason -> new MovementReasonResponse(reason.code(), reason.label(), reason.direction()))
                .toList();
    }
}
