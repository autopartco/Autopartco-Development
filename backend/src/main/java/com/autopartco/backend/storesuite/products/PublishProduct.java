package com.autopartco.backend.storesuite.products;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.catalog.ProductCatalog;
import com.autopartco.backend.core.catalog.ProductPublicationRequest;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;

import lombok.AllArgsConstructor;

/**
 * Publica o retira un producto del marketplace. Requiere {@code PRODUCT_PUBLISH}; publicar exige además una
 * suscripción activa (retirar no).
 */
@Service
@AllArgsConstructor
public class PublishProduct {
    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final PlanEntitlements planEntitlements;
    private final ProductCatalog productCatalog;

    @Transactional
    public void execute(UUID storeId, UUID productId, String email, ProductPublicationRequest request) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_PUBLISH);
        storeService.requireExists(storeId);
        if (request.published()) {
            planEntitlements.requireSubscriptionActive(storeId);
        }
        productCatalog.setPublished(storeId, productId, request.published());
    }
}
