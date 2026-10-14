package com.autopartco.backend.storesuite.products;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.core.catalog.ProductImageResponse;
import com.autopartco.backend.core.catalog.ProductImages;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;

import lombok.AllArgsConstructor;

/**
 * Gestión de las imágenes de un producto (subir, principal, orden, reemplazar, borrar). Todas las operaciones
 * requieren {@code PRODUCT_IMAGE_MANAGE}; el almacenamiento y la limpieza ante fallo los hace
 * {@code core.catalog.ProductImages}.
 */
@Service
@AllArgsConstructor
public class ManageProductImages {
    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final ProductImages productImages;

    @Transactional
    public List<ProductImageResponse> upload(UUID storeId, UUID productId, String email, List<MultipartFile> files) {
        requireImagePermission(storeId, email);
        return productImages.upload(storeId, productId, files);
    }

    @Transactional
    public ProductImageResponse setMain(UUID storeId, UUID productId, UUID imageId, String email) {
        requireImagePermission(storeId, email);
        return productImages.setMain(storeId, productId, imageId);
    }

    @Transactional
    public List<ProductImageResponse> reorder(UUID storeId, UUID productId, String email, List<UUID> imageIds) {
        requireImagePermission(storeId, email);
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
        requireImagePermission(storeId, email);
        return productImages.replace(storeId, productId, imageId, replacement);
    }

    @Transactional
    public void delete(UUID storeId, UUID productId, UUID imageId, String email) {
        requireImagePermission(storeId, email);
        productImages.delete(storeId, productId, imageId);
    }

    private void requireImagePermission(UUID storeId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_IMAGE_MANAGE);
        storeService.requireExists(storeId);
    }
}
