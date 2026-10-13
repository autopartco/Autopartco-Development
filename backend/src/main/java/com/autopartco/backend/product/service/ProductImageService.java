package com.autopartco.backend.product.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.product.dto.ProductImageResponse;
import com.autopartco.backend.product.entity.Product;
import com.autopartco.backend.product.entity.ProductImage;
import com.autopartco.backend.product.mapper.ProductImageMapper;
import com.autopartco.backend.product.repository.InventoryRepository;
import com.autopartco.backend.product.repository.ProductImageRepository;
import com.autopartco.backend.product.repository.ProductRepository;
import com.autopartco.backend.shared.storage.SupabaseStorageService;
import com.autopartco.backend.shared.storage.SupabaseStorageService.StoredObject;
import com.autopartco.backend.core.organization.StorePermissionCodes;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@AllArgsConstructor
@Slf4j
public class ProductImageService {
    private static final int MAX_IMAGES_PER_PRODUCT = 10;

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductImageValidator imageValidator;
    private final ProductImageMapper imageMapper;
    private final SupabaseStorageService storageService;

    @Transactional
    public List<ProductImageResponse> upload(
            UUID storeId,
            UUID productId,
            String email,
            List<MultipartFile> files
    ) {
        Product product = productService.getOwnedProductForUpdate(
                storeId, productId, email, StorePermissionCodes.PRODUCT_IMAGE_MANAGE);
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("PRODUCT_IMAGES_REQUIRED", "Selecciona al menos una imagen.");
        }

        List<ProductImage> existing = productImageRepository
                .findByProduct_IdOrderByDisplayOrderAscCreatedAtAsc(productId);
        if (existing.size() + files.size() > MAX_IMAGES_PER_PRODUCT) {
            throw new BadRequestException(
                    "PRODUCT_IMAGE_LIMIT_REACHED",
                    "Un producto puede tener como máximo 10 imágenes."
            );
        }
        List<ProductImageValidator.ValidatedImage> validatedFiles = files.stream()
                .map(imageValidator::validate)
                .toList();

        List<String> pathsToClean = new ArrayList<>();
        registerRollback(() -> cleanupUploadedFiles(pathsToClean));

        int displayOrder = productImageRepository.findMaxDisplayOrder(productId) + 1;
        boolean firstImage = existing.isEmpty();
        List<ProductImage> savedImages = new ArrayList<>();
        for (ProductImageValidator.ValidatedImage file : validatedFiles) {
            String path = storagePath(productId, file.extension());
            pathsToClean.add(path);
            storageService.upload(path, file.content(), file.contentType(), false);

            ProductImage image = ProductImage.builder()
                    .product(product)
                    .storagePath(path)
                    .contentType(file.contentType())
                    .isMain(firstImage && savedImages.isEmpty())
                    .displayOrder(displayOrder++)
                    .build();
            savedImages.add(productImageRepository.saveAndFlush(image));
        }
        return savedImages.stream().map(imageMapper::toResponse).toList();
    }

    @Transactional
    public ProductImageResponse setMain(
            UUID storeId,
            UUID productId,
            UUID imageId,
            String email
    ) {
        productService.getOwnedProductForUpdate(
                storeId, productId, email, StorePermissionCodes.PRODUCT_IMAGE_MANAGE);
        ProductImage image = findImage(productId, imageId);
        productImageRepository.clearMainImage(productId);
        image.setMain(true);
        return imageMapper.toResponse(productImageRepository.saveAndFlush(image));
    }

    @Transactional
    public List<ProductImageResponse> reorder(
            UUID storeId,
            UUID productId,
            String email,
            List<UUID> imageIds
    ) {
        productService.getOwnedProductForUpdate(
                storeId, productId, email, StorePermissionCodes.PRODUCT_IMAGE_MANAGE);
        List<ProductImage> images = productImageRepository
                .findByProduct_IdOrderByDisplayOrderAscCreatedAtAsc(productId);
        if (imageIds == null
                || imageIds.size() != images.size()
                || new HashSet<>(imageIds).size() != imageIds.size()) {
            throw invalidImageOrder();
        }

        Map<UUID, ProductImage> imagesById = images.stream()
                .collect(Collectors.toMap(ProductImage::getId, image -> image));
        List<ProductImage> reordered = new ArrayList<>(imageIds.size());
        for (int index = 0; index < imageIds.size(); index++) {
            ProductImage image = imagesById.get(imageIds.get(index));
            if (image == null) {
                throw invalidImageOrder();
            }
            image.setDisplayOrder(index);
            reordered.add(image);
        }
        return productImageRepository.saveAllAndFlush(reordered).stream()
                .map(imageMapper::toResponse)
                .toList();
    }

    @Transactional
    public ProductImageResponse replace(
            UUID storeId,
            UUID productId,
            UUID imageId,
            String email,
            MultipartFile replacement
    ) {
        Product product = productService.getOwnedProductForUpdate(
                storeId, productId, email, StorePermissionCodes.PRODUCT_IMAGE_MANAGE);
        ProductImage image = findImage(productId, imageId);
        ProductImageValidator.ValidatedImage validated = imageValidator.validate(replacement);
        String oldPath = image.getStoragePath();
        StoredObject previous = storageService.download(oldPath);
        String newPath = storagePath(productId, validated.extension());
        AtomicBoolean oldObjectDeleted = new AtomicBoolean();

        registerRollback(() -> {
            cleanupObject(newPath);
            if (oldObjectDeleted.get()) {
                restoreObject(oldPath, previous);
            }
        });

        storageService.upload(newPath, validated.content(), validated.contentType(), false);
        storageService.delete(oldPath);
        oldObjectDeleted.set(true);

        image.setStoragePath(newPath);
        image.setContentType(validated.contentType());
        return imageMapper.toResponse(productImageRepository.saveAndFlush(image));
    }

    @Transactional
    public void delete(
            UUID storeId,
            UUID productId,
            UUID imageId,
            String email
    ) {
        productService.getOwnedProductForUpdate(
                storeId, productId, email, StorePermissionCodes.PRODUCT_IMAGE_MANAGE);
        List<ProductImage> images = productImageRepository
                .findByProduct_IdOrderByDisplayOrderAscCreatedAtAsc(productId);
        ProductImage image = images.stream()
                .filter(candidate -> candidate.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "PRODUCT_IMAGE_NOT_FOUND",
                        "No se encontró la imagen del repuesto."
                ));
        StoredObject previous = storageService.download(image.getStoragePath());
        AtomicBoolean objectDeleted = new AtomicBoolean();
        registerRollback(() -> {
            if (objectDeleted.get()) {
                restoreObject(image.getStoragePath(), previous);
            }
        });

        storageService.delete(image.getStoragePath());
        objectDeleted.set(true);

        List<ProductImage> remaining = images.stream()
                .filter(candidate -> !candidate.getId().equals(imageId))
                .toList();
        if (image.isMain() && !remaining.isEmpty()) {
            productImageRepository.clearMainImage(productId);
            remaining.getFirst().setMain(true);
        }
        for (int index = 0; index < remaining.size(); index++) {
            remaining.get(index).setDisplayOrder(index);
        }
        productImageRepository.saveAll(remaining);
        productImageRepository.delete(image);
        productImageRepository.flush();
    }

    @Transactional
    public void deleteProduct(UUID storeId, UUID productId, String email) {
        Product product = productService.getOwnedProductForUpdate(
                storeId, productId, email, StorePermissionCodes.PRODUCT_DELETE);
        List<ProductImage> images = productImageRepository
                .findByProduct_IdOrderByDisplayOrderAscCreatedAtAsc(productId);
        List<ImageBackup> backups = images.stream()
                .map(image -> new ImageBackup(image.getStoragePath(), storageService.download(image.getStoragePath()),
                        new AtomicBoolean()))
                .toList();

        registerRollback(() -> backups.stream()
                .filter(backup -> backup.deleted().get())
                .forEach(backup -> restoreObject(backup.path(), backup.content())));

        for (ImageBackup backup : backups) {
            storageService.delete(backup.path());
            backup.deleted().set(true);
        }
        productImageRepository.deleteAllForProduct(productId);
        inventoryRepository.softDeleteAllForProduct(productId);
        productRepository.delete(product);
        productRepository.flush();
    }

    private ProductImage findImage(UUID productId, UUID imageId) {
        return productImageRepository.findByIdAndProduct_Id(imageId, productId)
                .orElseThrow(() -> new NotFoundException(
                        "PRODUCT_IMAGE_NOT_FOUND",
                        "No se encontró la imagen del repuesto."
                ));
    }

    private String storagePath(UUID productId, String extension) {
        return "products/" + productId + "/" + UUID.randomUUID() + "." + extension;
    }

    private BadRequestException invalidImageOrder() {
        return new BadRequestException(
                "INVALID_PRODUCT_IMAGE_ORDER",
                "El orden debe incluir todas las imágenes del producto una sola vez."
        );
    }

    private void registerRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Las operaciones de imágenes requieren una transacción activa.");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    action.run();
                }
            }
        });
    }

    private void cleanupUploadedFiles(List<String> paths) {
        paths.forEach(this::cleanupObject);
    }

    private void cleanupObject(String path) {
        try {
            storageService.delete(path);
        } catch (RuntimeException exception) {
            log.error("Could not clean up Supabase Storage object {}", path, exception);
        }
    }

    private void restoreObject(String path, StoredObject object) {
        try {
            storageService.upload(path, object.content(), object.contentType(), true);
        } catch (RuntimeException exception) {
            log.error("Could not restore Supabase Storage object {}", path, exception);
        }
    }

    private record ImageBackup(String path, StoredObject content, AtomicBoolean deleted) {
    }
}
