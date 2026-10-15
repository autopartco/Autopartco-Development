package com.autopartco.backend.storesuite.products;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.catalog.ProductCatalog;
import com.autopartco.backend.core.catalog.StoreProductDetail;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;

import lombok.AllArgsConstructor;

/** Detalle de un producto del almacén con sus listados por sucursal. Requiere {@code PRODUCT_READ}. */
@Service
@AllArgsConstructor
public class GetStoreProduct {
    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final ProductCatalog productCatalog;

    @Transactional(readOnly = true)
    public StoreProductDetail execute(UUID storeId, UUID productId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_READ);
        storeService.requireExists(storeId);
        return productCatalog.detail(storeId, productId);
    }
}
