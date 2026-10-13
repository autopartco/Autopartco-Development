package com.autopartco.backend.product.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.core.catalog.ProductCatalog;
import com.autopartco.backend.core.catalog.ProductImageResponse;
import com.autopartco.backend.core.catalog.ProductImages;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;

import lombok.AllArgsConstructor;

/**
 * Temporal (fase 4a): permiso del usuario antes de {@code core.catalog.ProductImages} y del borrado de producto. En
 * la fase 4b pasa a {@code storesuite.products}.
 */
@Service
@AllArgsConstructor
public class ProductImageService {
    private final ProductImages productImages;
    private final ProductCatalog productCatalog;
    private final StoreAccess storeAccess;
    private final StoreService storeService;

    @Transactional
    public List<ProductImageResponse> upload(UUID storeId, UUID productId, String email, List<MultipartFile> files) {
        requirePermission(storeId, email, StorePermissionCodes.PRODUCT_IMAGE_MANAGE);
        return productImages.upload(storeId, productId, files);
    }

    @Transactional
    public ProductImageResponse setMain(UUID storeId, UUID productId, UUID imageId, String email) {
        requirePermission(storeId, email, StorePermissionCodes.PRODUCT_IMAGE_MANAGE);
        return productImages.setMain(storeId, productId, imageId);
    }

    @Transactional
    public List<ProductImageResponse> reorder(UUID storeId, UUID productId, String email, List<UUID> imageIds) {
        requirePermission(storeId, email, StorePermissionCodes.PRODUCT_IMAGE_MANAGE);
        return productImages.reorder(storeId, productId, imageIds);
    }

    @Transactional
    public ProductImageResponse replace(
            UUID storeId,
            UUID productId,
            UUID imageId,
            String email,
            MultipartFile replacement
    ) {
        requirePermission(storeId, email, StorePermissionCodes.PRODUCT_IMAGE_MANAGE);
        return productImages.replace(storeId, productId, imageId, replacement);
    }

    @Transactional
    public void delete(UUID storeId, UUID productId, UUID imageId, String email) {
        requirePermission(storeId, email, StorePermissionCodes.PRODUCT_IMAGE_MANAGE);
        productImages.delete(storeId, productId, imageId);
    }

    @Transactional
    public void deleteProduct(UUID storeId, UUID productId, String email) {
        requirePermission(storeId, email, StorePermissionCodes.PRODUCT_DELETE);
        productCatalog.delete(storeId, productId);
    }

    private void requirePermission(UUID storeId, String email, String permissionCode) {
        storeAccess.requirePermission(email, storeId, permissionCode);
        storeService.requireExists(storeId);
    }
}
