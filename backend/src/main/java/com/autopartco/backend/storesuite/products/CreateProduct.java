package com.autopartco.backend.storesuite.products;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.catalog.ProductCatalog;
import com.autopartco.backend.core.catalog.ProductCreateRequest;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;

import lombok.AllArgsConstructor;

/**
 * Alta atómica de un producto en el almacén (datos, fabricante nuevo, reglas de vehículo, imágenes y publicación en
 * una sola transacción). Permisos por parte: {@code PRODUCT_CREATE} siempre, {@code PRODUCT_IMAGE_MANAGE} si hay
 * imágenes y {@code PRODUCT_PUBLISH} si {@code publish=true}; todos se verifican antes de tocar nada. Bloquea la fila
 * del almacén antes de contar el cupo para que dos altas simultáneas no superen el límite del plan.
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
        return execute(storeId, email, request, List.of());
    }

    @Transactional
    public ProductResponse execute(UUID storeId, String email, ProductCreateRequest request,
            List<MultipartFile> images) {
        List<MultipartFile> files = images == null ? List.of() : images;
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_CREATE);
        if (!files.isEmpty()) {
            storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_IMAGE_MANAGE);
        }
        if (request.publishRequested()) {
            storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_PUBLISH);
        }
        storeService.requireExists(storeId);
        storeService.lockForUpdate(storeId);
        boolean subscriptionActive = !request.publishRequested() || planEntitlements.isSubscriptionActive(storeId);
        planEntitlements.requireCapacity(storeId, PlanEntitlements.Capacity.PRODUCTS, 1);
        return productCatalog.create(storeId, request, files, subscriptionActive);
    }
}
