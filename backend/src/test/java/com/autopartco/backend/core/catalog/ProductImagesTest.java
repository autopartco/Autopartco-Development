package com.autopartco.backend.core.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.storage.StorageException;
import com.autopartco.backend.core.catalog.internal.Product;
import com.autopartco.backend.core.catalog.internal.ProductImage;
import com.autopartco.backend.core.catalog.internal.ProductImageMapper;
import com.autopartco.backend.core.catalog.internal.ProductImageRepository;
import com.autopartco.backend.core.catalog.internal.ProductImageValidator;
import com.autopartco.backend.core.catalog.internal.ProductRepository;
import com.autopartco.backend.shared.storage.SupabaseStorageService;

@ExtendWith(MockitoExtension.class)
class ProductImagesTest {
    private static final String ONE_PIXEL_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aN9sAAAAASUVORK5CYII=";
    private static final UUID STORE_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private ProductImageMapper imageMapper;
    @Mock
    private SupabaseStorageService storageService;

    private ProductImages service;
    private Product product;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        product = Product.builder().build();
        service = new ProductImages(
                productRepository,
                productImageRepository,
                new ProductImageValidator(),
                imageMapper,
                storageService
        );
        // Cada operación bloquea el producto acotado por almacén (antes, ProductService#getOwnedProductForUpdate).
        when(productRepository.findOwnedForUpdate(STORE_ID, PRODUCT_ID)).thenReturn(Optional.of(product));
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.clear();
    }

    @Test
    void firstImageInBatchBecomesMainAndLaterImagesReceiveOrder() {
        when(productImageRepository.findByProduct_IdOrderByDisplayOrderAscCreatedAtAsc(PRODUCT_ID))
                .thenReturn(List.of());
        when(productImageRepository.findMaxDisplayOrder(PRODUCT_ID)).thenReturn(-1);
        when(productImageRepository.saveAndFlush(any(ProductImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(imageMapper.toResponse(any(ProductImage.class))).thenAnswer(invocation -> {
            ProductImage image = invocation.getArgument(0);
            return new ProductImageResponse(
                    UUID.randomUUID(),
                    "https://images.example/" + image.getStoragePath(),
                    image.isMain(),
                    image.getDisplayOrder(),
                    null
            );
        });

        List<ProductImageResponse> response = service.upload(
                STORE_ID,
                PRODUCT_ID,
                List.of(png("one.png"), png("two.png"))
        );

        ArgumentCaptor<ProductImage> imageCaptor = ArgumentCaptor.forClass(ProductImage.class);
        verify(productImageRepository, org.mockito.Mockito.times(2)).saveAndFlush(imageCaptor.capture());
        List<ProductImage> saved = imageCaptor.getAllValues();

        assertEquals(2, response.size());
        assertEquals("products/" + PRODUCT_ID + "/", saved.get(0).getStoragePath().substring(
                0, saved.get(0).getStoragePath().lastIndexOf('/') + 1));
        assertEquals(true, saved.get(0).isMain());
        assertEquals(false, saved.get(1).isMain());
        assertEquals(0, saved.get(0).getDisplayOrder());
        assertEquals(1, saved.get(1).getDisplayOrder());
        verify(storageService, org.mockito.Mockito.times(2))
                .upload(anyString(), any(byte[].class), eq("image/png"), eq(false));
    }

    @Test
    void rejectsAdditionalImagesWhenProductLimitWouldBeExceeded() {
        List<ProductImage> existing = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            existing.add(ProductImage.builder().id(UUID.randomUUID()).build());
        }
        when(productImageRepository.findByProduct_IdOrderByDisplayOrderAscCreatedAtAsc(PRODUCT_ID))
                .thenReturn(existing);

        assertThrows(BadRequestException.class, () -> service.upload(
                STORE_ID, PRODUCT_ID, List.of(png("extra.png"))));

        verify(storageService, never()).upload(anyString(), any(byte[].class), anyString(), eq(false));
        verify(productImageRepository, never()).saveAndFlush(any(ProductImage.class));
    }

    @Test
    void removesUploadedObjectIfTheDatabaseTransactionRollsBack() {
        when(productImageRepository.findByProduct_IdOrderByDisplayOrderAscCreatedAtAsc(PRODUCT_ID))
                .thenReturn(List.of());
        doThrow(new StorageException("storage unavailable"))
                .when(storageService).upload(anyString(), any(byte[].class), anyString(), eq(false));

        assertThrows(StorageException.class, () -> service.upload(
                STORE_ID, PRODUCT_ID, List.of(png("one.png"))));

        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(storageService).delete(anyString());
        verify(productImageRepository, never()).saveAndFlush(any(ProductImage.class));
    }

    @Test
    void deletingMainImagePromotesTheNextImageAndClosesTheOrderGap() {
        UUID mainId = UUID.randomUUID();
        UUID nextId = UUID.randomUUID();
        ProductImage main = ProductImage.builder()
                .id(mainId)
                .product(product)
                .storagePath("products/" + PRODUCT_ID + "/main.png")
                .contentType("image/png")
                .isMain(true)
                .displayOrder(0)
                .build();
        ProductImage next = ProductImage.builder()
                .id(nextId)
                .product(product)
                .storagePath("products/" + PRODUCT_ID + "/next.png")
                .contentType("image/png")
                .isMain(false)
                .displayOrder(1)
                .build();
        when(productImageRepository.findByProduct_IdOrderByDisplayOrderAscCreatedAtAsc(PRODUCT_ID))
                .thenReturn(List.of(main, next));
        when(storageService.download(main.getStoragePath()))
                .thenReturn(new SupabaseStorageService.StoredObject(new byte[] {1}, "image/png"));

        service.delete(STORE_ID, PRODUCT_ID, mainId);

        assertEquals(true, next.isMain());
        assertEquals(0, next.getDisplayOrder());
        verify(productImageRepository).clearMainImage(PRODUCT_ID);
        verify(productImageRepository).delete(main);
        verify(storageService).delete(main.getStoragePath());
    }

    private MockMultipartFile png(String filename) {
        return new MockMultipartFile(
                "files",
                filename,
                "image/png",
                Base64.getDecoder().decode(ONE_PIXEL_PNG)
        );
    }
}
