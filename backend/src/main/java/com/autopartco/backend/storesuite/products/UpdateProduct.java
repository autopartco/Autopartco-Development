package com.autopartco.backend.storesuite.products;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.catalog.ProductCatalog;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.catalog.ProductUpdateRequest;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;

import lombok.AllArgsConstructor;

/**
 * Edición atómica de un producto y su inventario en una sucursal. Permisos por parte: {@code PRODUCT_UPDATE} si
 * cambian datos, {@code PRODUCT_IMAGE_MANAGE} si cambia la galería y {@code PRODUCT_PUBLISH} si viene
 * {@code publish}; todos antes de tocar nada.
 */
@Service
@AllArgsConstructor
public class UpdateProduct {
    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final PlanEntitlements planEntitlements;
    private final ProductCatalog productCatalog;

    @Transactional
    public ProductResponse execute(
            UUID storeId,
            UUID branchId,
            UUID productId,
            String email,
            ProductUpdateRequest request
    ) {
        return execute(storeId, branchId, productId, email, request, List.of());
    }

    @Transactional
    public ProductResponse execute(
            UUID storeId,
            UUID branchId,
            UUID productId,
            String email,
            ProductUpdateRequest request,
            List<MultipartFile> newImages
    ) {
        List<MultipartFile> files = newImages == null ? List.of() : newImages;
        boolean galleryTouched = !files.isEmpty() || request.changesGallery();
        // Sin cambios de datos (solo galería o publicación) no se exige PRODUCT_UPDATE; sin nada, lo exige y el core
        // responde NO_CHANGES.
        if (request.changesProductData() || (!galleryTouched && request.publish() == null)) {
            storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_UPDATE);
        }
        if (galleryTouched) {
            storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_IMAGE_MANAGE);
        }
        if (request.publish() != null) {
            storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_PUBLISH);
        }
        storeService.requireExists(storeId);
        boolean subscriptionActive = !Boolean.TRUE.equals(request.publish())
                || planEntitlements.isSubscriptionActive(storeId);
        return productCatalog.update(storeId, branchId, productId, request, files, subscriptionActive);
    }
}
