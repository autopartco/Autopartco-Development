package com.autopartco.backend.core.catalog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.autopartco.backend.core.catalog.internal.Product;
import com.autopartco.backend.core.catalog.internal.ProductImage;
import com.autopartco.backend.core.catalog.internal.ProductImageMapper;
import com.autopartco.backend.core.catalog.internal.ProductImageRepository;
import com.autopartco.backend.core.catalog.internal.ProductImageValidator;
import com.autopartco.backend.core.catalog.internal.ProductRepository;
import com.autopartco.backend.shared.storage.SupabaseStorageService;
import com.autopartco.backend.shared.storage.SupabaseStorageService.StoredObject;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Imágenes de un producto del almacén (Supabase Storage + {@code product_images}), con compensación en Storage si
 * la transacción se revierte. Cada operación bloquea el producto acotado por {@code storeId}. No resuelve permisos:
 * lo hacen los casos de uso de los productos.
 */
@Service
@AllArgsConstructor
@Slf4j
public class ProductImages {
    private static final int MAX_IMAGES_PER_PRODUCT = 10;

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductImageValidator imageValidator;
    private final ProductImageMapper imageMapper;
    private final SupabaseStorageService storageService;

    @Transactional
    public List<ProductImageResponse> upload(
            UUID storeId,
            UUID productId,
            List<MultipartFile> files
    ) {
        Product product = lockOwned(storeId, productId);
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("PRODUCT_IMAGES_REQUIRED", "Selecciona al menos una imagen.");
        }

        List<ProductImage> existing = productImageRepository
                .findAllOfProduct(productId);
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
            UUID imageId
    ) {
        lockOwned(storeId, productId);
        ProductImage image = findImage(productId, imageId);
        productImageRepository.clearMainImage(productId);
        image.setMain(true);
        return imageMapper.toResponse(productImageRepository.saveAndFlush(image));
    }

    @Transactional
    public List<ProductImageResponse> reorder(
            UUID storeId,
            UUID productId,
            List<UUID> imageIds
    ) {
        lockOwned(storeId, productId);
        List<ProductImage> images = productImageRepository
                .findAllOfProduct(productId);
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
            MultipartFile replacement
    ) {
        Product product = lockOwned(storeId, productId);
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
            UUID imageId
    ) {
        lockOwned(storeId, productId);
        List<ProductImage> images = productImageRepository
                .findAllOfProduct(productId);
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

    /**
     * Borra todas las imágenes del producto (Storage y filas) como parte del borrado del producto. El llamador
     * ({@link ProductCatalog#delete}) ya bloqueó el producto.
     */
    @Transactional
    public void deleteAllForProduct(UUID productId) {
        List<ProductImage> images = productImageRepository
                .findAllOfProduct(productId);
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
    }

    @Transactional(readOnly = true)
    public long count(UUID productId) {
        return productImageRepository.countByProduct_Id(productId);
    }

    /** Valida (tipo real, tamaño, dimensiones) sin tocar Storage. Más de 10 archivos: {@code PRODUCT_IMAGE_LIMIT_REACHED}. */
    public List<ProductImageValidator.ValidatedImage> validateAll(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        if (files.size() > MAX_IMAGES_PER_PRODUCT) {
            throw imageLimitReached();
        }
        return files.stream().map(imageValidator::validate).toList();
    }

    /**
     * Calcula, sin escribir nada, la galería resultante de un alta o edición atómica. {@code productId} nulo = producto
     * nuevo (sin imágenes). {@code orderTokens} nulo conserva el orden actual y agrega las nuevas al final; si se
     * envía, debe nombrar exactamente una vez cada imagen que queda (id) y cada nueva ({@code new:<i>}).
     * {@code mainToken} nulo conserva la principal si sigue, si no la primera.
     */
    @Transactional(readOnly = true)
    public GalleryPlan planGallery(
            UUID productId,
            Set<UUID> removeImageIds,
            List<ProductImageValidator.ValidatedImage> newImages,
            List<String> orderTokens,
            String mainToken
    ) {
        List<ProductImage> existing = productId == null ? List.of() : productImageRepository.findAllOfProduct(productId);
        Set<UUID> removeIds = removeImageIds == null ? Set.of() : removeImageIds;
        Map<UUID, ProductImage> existingById = new LinkedHashMap<>();
        existing.forEach(image -> existingById.put(image.getId(), image));
        for (UUID id : removeIds) {
            if (!existingById.containsKey(id)) {
                throw imageNotFound();
            }
        }
        List<ProductImage> removed = existing.stream().filter(image -> removeIds.contains(image.getId())).toList();
        List<ProductImage> kept = existing.stream().filter(image -> !removeIds.contains(image.getId())).toList();
        int total = kept.size() + newImages.size();
        if (total > MAX_IMAGES_PER_PRODUCT) {
            throw imageLimitReached();
        }

        List<Slot> order;
        if (orderTokens == null) {
            order = new ArrayList<>();
            kept.forEach(image -> order.add(Slot.existing(image)));
            for (int index = 0; index < newImages.size(); index++) {
                order.add(Slot.fresh(index));
            }
        } else {
            order = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String token : orderTokens) {
                Slot slot = parseSlot(token, kept, newImages.size());
                if (slot == null || !seen.add(slot.key())) {
                    throw invalidImageOrder();
                }
                order.add(slot);
            }
            if (order.size() != total) {
                throw invalidImageOrder();
            }
        }

        Slot main = null;
        if (mainToken != null && !mainToken.isBlank()) {
            Slot requested = parseSlot(mainToken, kept, newImages.size());
            if (requested == null) {
                throw new BadRequestException("INVALID_PRODUCT_IMAGE_MAIN",
                        "La imagen principal debe ser una de las imágenes del repuesto.");
            }
            main = requested;
        } else if (!order.isEmpty()) {
            main = order.stream()
                    .filter(slot -> slot.image() != null && slot.image().isMain())
                    .findFirst()
                    .orElse(order.getFirst());
        }
        return new GalleryPlan(removed, newImages, List.copyOf(order), main);
    }

    /**
     * Aplica un {@link GalleryPlan} al producto ya bloqueado por el llamador: sube las nuevas, borra las quitadas,
     * ordena y marca la principal. Si la transacción se revierte, borra de Storage lo subido y restaura lo borrado.
     */
    @Transactional
    public void applyGallery(Product product, GalleryPlan plan) {
        UUID productId = product.getId();
        List<ImageBackup> backups = plan.removed().stream()
                .map(image -> new ImageBackup(image.getStoragePath(), storageService.download(image.getStoragePath()),
                        new AtomicBoolean()))
                .toList();
        List<String> uploadedPaths = new ArrayList<>();
        registerRollback(() -> {
            cleanupUploadedFiles(uploadedPaths);
            backups.stream()
                    .filter(backup -> backup.deleted().get())
                    .forEach(backup -> restoreObject(backup.path(), backup.content()));
        });

        List<ProductImage> created = new ArrayList<>();
        for (ProductImageValidator.ValidatedImage file : plan.newImages()) {
            String path = storagePath(productId, file.extension());
            uploadedPaths.add(path);
            storageService.upload(path, file.content(), file.contentType(), false);
            created.add(productImageRepository.save(ProductImage.builder()
                    .product(product)
                    .storagePath(path)
                    .contentType(file.contentType())
                    .isMain(false)
                    .displayOrder(MAX_IMAGES_PER_PRODUCT + created.size())
                    .build()));
        }

        for (ImageBackup backup : backups) {
            storageService.delete(backup.path());
            backup.deleted().set(true);
        }
        if (!plan.removed().isEmpty()) {
            productImageRepository.deleteAll(plan.removed());
        }

        List<ProductImage> ordered = plan.order().stream()
                .map(slot -> slot.image() != null ? slot.image() : created.get(slot.newIndex()))
                .toList();
        for (int index = 0; index < ordered.size(); index++) {
            ordered.get(index).setDisplayOrder(index);
        }
        productImageRepository.flush();

        ProductImage main = plan.main() == null ? null
                : plan.main().image() != null ? plan.main().image() : created.get(plan.main().newIndex());
        // Índice único de principal: primero se limpia en BD y luego se marca con UPDATE directos.
        productImageRepository.clearMainImage(productId);
        if (main != null) {
            productImageRepository.markMain(productId, main.getId());
        }
        ordered.forEach(image -> image.setMain(image == main));
        productImageRepository.flush();
    }

    /** Galería resultante calculada por {@link #planGallery}; {@code order} y {@code main} usan {@link Slot}. */
    public record GalleryPlan(
            List<ProductImage> removed,
            List<ProductImageValidator.ValidatedImage> newImages,
            List<Slot> order,
            Slot main
    ) {
        public int finalCount() {
            return order.size();
        }

        public boolean changesSomething() {
            return !removed.isEmpty() || !newImages.isEmpty();
        }
    }

    /** Posición de la galería: una imagen existente ({@code image}) o la nueva número {@code newIndex}. */
    public record Slot(ProductImage image, Integer newIndex) {
        static Slot existing(ProductImage image) {
            return new Slot(image, null);
        }

        static Slot fresh(int index) {
            return new Slot(null, index);
        }

        String key() {
            return image != null ? image.getId().toString() : NEW_TOKEN_PREFIX + newIndex;
        }
    }

    static final String NEW_TOKEN_PREFIX = "new:";

    private static Slot parseSlot(String token, List<ProductImage> kept, int newCount) {
        if (token == null) {
            return null;
        }
        String value = token.strip();
        if (value.startsWith(NEW_TOKEN_PREFIX)) {
            try {
                int index = Integer.parseInt(value.substring(NEW_TOKEN_PREFIX.length()));
                return index >= 0 && index < newCount ? Slot.fresh(index) : null;
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        UUID id;
        try {
            id = UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return kept.stream().filter(image -> image.getId().equals(id)).findFirst().map(Slot::existing).orElse(null);
    }

    private static BadRequestException imageLimitReached() {
        return new BadRequestException(
                "PRODUCT_IMAGE_LIMIT_REACHED",
                "Un producto puede tener como máximo 10 imágenes."
        );
    }

    private static NotFoundException imageNotFound() {
        return new NotFoundException("PRODUCT_IMAGE_NOT_FOUND", "No se encontró la imagen del repuesto.");
    }

    private Product lockOwned(UUID storeId, UUID productId) {
        return productRepository.lockOwned(storeId, productId)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "No se encontró el repuesto."));
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
