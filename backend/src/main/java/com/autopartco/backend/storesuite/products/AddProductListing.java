package com.autopartco.backend.storesuite.products;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.catalog.ProductCatalog;
import com.autopartco.backend.core.catalog.ProductListingRequest;
import com.autopartco.backend.core.catalog.StoreProductDetail;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;

import lombok.AllArgsConstructor;

/** Agrega un producto a otra sucursal. Requiere {@code PRODUCT_UPDATE} y suscripción vigente. */
@Service
@AllArgsConstructor
public class AddProductListing {
    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final PlanEntitlements planEntitlements;
    private final ProductCatalog productCatalog;

    @Transactional
    public StoreProductDetail execute(UUID storeId, UUID productId, String email, ProductListingRequest request) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_UPDATE);
        storeService.requireExists(storeId);
        planEntitlements.requireSubscriptionActive(storeId);
        return productCatalog.addListing(storeId, productId, request);
    }
}
