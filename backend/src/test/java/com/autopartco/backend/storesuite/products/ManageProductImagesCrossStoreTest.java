package com.autopartco.backend.storesuite.products;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.catalog.ProductImages;
import com.autopartco.backend.core.catalog.internal.Product;
import com.autopartco.backend.core.catalog.internal.ProductImageMapper;
import com.autopartco.backend.core.catalog.internal.ProductImageRepository;
import com.autopartco.backend.core.catalog.internal.ProductImageValidator;
import com.autopartco.backend.core.catalog.internal.ProductRepository;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.storage.SupabaseStorageService;

/**
 * IDOR entre almacenes en las imágenes de producto: con permiso en el almacén A no se puede tocar un producto de
 * B ni, desde un producto propio, una imagen de otro producto.
 */
@ExtendWith(MockitoExtension.class)
class ManageProductImagesCrossStoreTest {
    private static final String EMAIL = "staff-a@example.com";
    private static final UUID STORE_A = UUID.randomUUID();
    private static final UUID STORE_B = UUID.randomUUID();
    private static final UUID PRODUCT_OF_A = UUID.randomUUID();
    private static final UUID PRODUCT_OF_B = UUID.randomUUID();
    private static final UUID IMAGE_OF_B = UUID.randomUUID();

    @Mock
    private StoreAccess storeAccess;
    @Mock
    private StoreService storeService;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private ProductImageMapper imageMapper;
    @Mock
    private SupabaseStorageService storageService;

    private ManageProductImages useCase() {
        return new ManageProductImages(storeAccess, storeService, new ProductImages(
                productRepository, productImageRepository, new ProductImageValidator(), imageMapper, storageService));
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "a.png", "image/png", new byte[] { 1 });
    }

    private static void assertNotFound(String code, Executable call) {
        assertEquals(code, assertThrows(NotFoundException.class, call).getCode());
    }

    @Test
    void productOfAnotherStoreThroughOwnStoreIsNotFoundForEveryOperation() {
        when(productRepository.lockOwned(STORE_A, PRODUCT_OF_B)).thenReturn(Optional.empty());
        ManageProductImages useCase = useCase();

        assertNotFound("PRODUCT_NOT_FOUND", () -> useCase.upload(STORE_A, PRODUCT_OF_B, EMAIL, List.of(file())));
        assertNotFound("PRODUCT_NOT_FOUND", () -> useCase.setMain(STORE_A, PRODUCT_OF_B, IMAGE_OF_B, EMAIL));
        assertNotFound("PRODUCT_NOT_FOUND", () -> useCase.reorder(STORE_A, PRODUCT_OF_B, EMAIL, List.of(IMAGE_OF_B)));
        assertNotFound("PRODUCT_NOT_FOUND", () -> useCase.replace(STORE_A, PRODUCT_OF_B, IMAGE_OF_B, EMAIL, file()));
        assertNotFound("PRODUCT_NOT_FOUND", () -> useCase.delete(STORE_A, PRODUCT_OF_B, IMAGE_OF_B, EMAIL));

        verifyNoInteractions(productImageRepository, storageService);
    }

    @Test
    void imageOfAnotherProductThroughOwnProductIsNotFound() {
        when(productRepository.lockOwned(STORE_A, PRODUCT_OF_A)).thenReturn(Optional.of(Product.builder().build()));
        when(productImageRepository.findByIdAndProduct_Id(IMAGE_OF_B, PRODUCT_OF_A)).thenReturn(Optional.empty());
        when(productImageRepository.findAllOfProduct(PRODUCT_OF_A)).thenReturn(List.of());
        ManageProductImages useCase = useCase();

        assertNotFound("PRODUCT_IMAGE_NOT_FOUND", () -> useCase.setMain(STORE_A, PRODUCT_OF_A, IMAGE_OF_B, EMAIL));
        assertNotFound("PRODUCT_IMAGE_NOT_FOUND",
                () -> useCase.replace(STORE_A, PRODUCT_OF_A, IMAGE_OF_B, EMAIL, file()));
        assertNotFound("PRODUCT_IMAGE_NOT_FOUND", () -> useCase.delete(STORE_A, PRODUCT_OF_A, IMAGE_OF_B, EMAIL));

        verify(productImageRepository, never()).clearMainImage(PRODUCT_OF_A);
        verifyNoInteractions(storageService);
    }

    @Test
    void anotherStoreWithoutPermissionIsForbiddenBeforeTouchingTheProduct() {
        doThrow(new AccessDeniedException("Store permission denied: " + StorePermissionCodes.PRODUCT_IMAGE_MANAGE))
                .when(storeAccess).requirePermission(EMAIL, STORE_B, StorePermissionCodes.PRODUCT_IMAGE_MANAGE);

        assertThrows(AccessDeniedException.class,
                () -> useCase().delete(STORE_B, PRODUCT_OF_B, IMAGE_OF_B, EMAIL));

        verifyNoInteractions(storeService, productRepository, productImageRepository, storageService);
    }
}
