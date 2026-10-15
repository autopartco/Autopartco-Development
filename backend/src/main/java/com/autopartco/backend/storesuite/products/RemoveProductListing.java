package com.autopartco.backend.storesuite.products;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.catalog.ProductCatalog;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;

import lombok.AllArgsConstructor;

/** Quita el listado de un producto en una sucursal (nunca el último). Requiere {@code PRODUCT_UPDATE}. */
@Service
@AllArgsConstructor
public class RemoveProductListing {
    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final ProductCatalog productCatalog;

    @Transactional
    public void execute(UUID storeId, UUID productId, UUID branchId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_UPDATE);
        storeService.requireExists(storeId);
        productCatalog.removeListing(storeId, productId, branchId);
    }
}
