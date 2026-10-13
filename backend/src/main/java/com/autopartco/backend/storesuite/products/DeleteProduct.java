package com.autopartco.backend.storesuite.products;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.catalog.ProductCatalog;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;

import lombok.AllArgsConstructor;

/** Borra un producto del almacén (con sus imágenes e inventario). Requiere {@code PRODUCT_DELETE}. */
@Service
@AllArgsConstructor
public class DeleteProduct {
    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final ProductCatalog productCatalog;

    @Transactional
    public void execute(UUID storeId, UUID productId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_DELETE);
        storeService.requireExists(storeId);
        productCatalog.delete(storeId, productId);
    }
}
