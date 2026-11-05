package com.autopartco.backend.storesuite.stockalerts;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.inventory.InventoryQueries;
import com.autopartco.backend.core.inventory.ReorderPolicies;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/**
 * Alertas de stock bajo y stock mínimo por listado. Umbral: punto de reorden de la política vigente o
 * {@value InventoryQueries#DEFAULT_LOW_STOCK_THRESHOLD} sin política.
 */
@Service
@AllArgsConstructor
public class LowStock {
    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final AccountLookup accountLookup;
    private final InventoryQueries inventoryQueries;
    private final ReorderPolicies reorderPolicies;

    /** Listados con {@code stock ≤} stock mínimo, opcionalmente de una sucursal. Requiere {@code PRODUCT_READ}. */
    @Transactional(readOnly = true)
    public List<LowStockResponse> list(UUID storeId, String email, UUID branchId) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_READ);
        return inventoryQueries.lowStock(storeId, branchId).stream()
                .map(item -> new LowStockResponse(item.productId(), item.productName(), item.sku(), item.branchId(),
                        item.branchName(), item.stock(), item.minStock()))
                .toList();
    }

    /**
     * Fija el stock mínimo del listado (cierra la política vigente e inserta otra {@code MANUAL}); {@code null} solo
     * la cierra. Requiere {@code PRODUCT_UPDATE}; listado de otro almacén o inexistente: {@code LISTING_NOT_FOUND}.
     */
    @Transactional
    public void setMinStock(UUID storeId, UUID branchId, UUID productId, String email, Integer minStock) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_UPDATE);
        storeService.requireExists(storeId);
        UUID inventoryId = inventoryQueries.listing(storeId, branchId, productId)
                .orElseThrow(() -> new NotFoundException(
                        "LISTING_NOT_FOUND", "El producto no está listado en esa sucursal."))
                .inventoryId();
        UUID actorAccountId = accountLookup.requireByEmail(email).accountId();
        reorderPolicies.setMinStock(storeId, inventoryId, minStock, actorAccountId);
    }
}
