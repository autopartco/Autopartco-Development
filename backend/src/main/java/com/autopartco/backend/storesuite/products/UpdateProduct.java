package com.autopartco.backend.storesuite.products;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.catalog.ProductCatalog;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.catalog.ProductUpdateRequest;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;

import lombok.AllArgsConstructor;

/** Actualiza un producto y su inventario en una sucursal. Requiere {@code PRODUCT_UPDATE}. */
@Service
@AllArgsConstructor
public class UpdateProduct {
    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final ProductCatalog productCatalog;

    @Transactional
    public ProductResponse execute(
            UUID storeId,
            UUID branchId,
            UUID productId,
            String email,
            ProductUpdateRequest request
    ) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_UPDATE);
        storeService.requireExists(storeId);
        return productCatalog.update(storeId, branchId, productId, request);
    }
}
