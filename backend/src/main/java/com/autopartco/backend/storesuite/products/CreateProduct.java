package com.autopartco.backend.storesuite.products;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.catalog.ProductCatalog;
import com.autopartco.backend.core.catalog.ProductCreateRequest;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;

import lombok.AllArgsConstructor;

/**
 * Crea un producto en el almacén. Requiere {@code PRODUCT_CREATE} y cupo de productos en el plan. Bloquea la fila
 * del almacén antes de contar el cupo para que dos altas simultáneas no superen el límite.
 */
@Service
@AllArgsConstructor
public class CreateProduct {
    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final PlanEntitlements planEntitlements;
    private final ProductCatalog productCatalog;

    @Transactional
    public ProductResponse execute(UUID storeId, String email, ProductCreateRequest request) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_CREATE);
        storeService.requireExists(storeId);
        storeService.lockForUpdate(storeId);
        planEntitlements.requireCapacity(storeId, PlanEntitlements.Capacity.PRODUCTS, 1);
        return productCatalog.create(storeId, request);
    }
}
