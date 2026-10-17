package com.autopartco.backend.storesuite.products;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.catalog.ProductCatalog;
import com.autopartco.backend.core.catalog.ProductCreateRequest;
import com.autopartco.backend.core.catalog.ProductImages;
import com.autopartco.backend.core.catalog.ProductPublicationRequest;
import com.autopartco.backend.core.catalog.ProductUpdateRequest;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.shared.error.ConflictException;

/** Orden de chequeos de los casos de uso de productos del almacén (permisos por parte, almacén, plan, core). */
@ExtendWith(MockitoExtension.class)
class StoreProductUseCasesTest {
    private static final String EMAIL = "staff@example.com";

    @Mock
    private StoreAccess storeAccess;

    @Mock
    private StoreService storeService;

    @Mock
    private PlanEntitlements planEntitlements;

    @Mock
    private ProductCatalog productCatalog;

    @Mock
    private ProductImages productImages;

    private final UUID storeId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();

    private static ProductCreateRequest createRequest(boolean publish) {
        return new ProductCreateRequest(UUID.randomUUID(), "Filtro", null, null, "desc", new BigDecimal("10.00"),
                0, UUID.randomUUID(), UUID.randomUUID(), null, null, List.of(), null, Map.of(), null, publish);
    }

    private static List<MultipartFile> oneImage() {
        return List.of(new MockMultipartFile("images", "a.png", "image/png", new byte[] {1}));
    }

    private CreateProduct createProduct() {
        return new CreateProduct(storeAccess, storeService, planEntitlements, productCatalog);
    }

    private UpdateProduct updateProduct() {
        return new UpdateProduct(storeAccess, storeService, planEntitlements, productCatalog);
    }

    private void deny(String permission) {
        org.mockito.Mockito.lenient().doThrow(new AccessDeniedException("denied"))
                .when(storeAccess).requirePermission(EMAIL, storeId, permission);
    }

    @Test
    void createChecksPermissionStoreLockAndCapacityBeforeCreating() {
        ProductCreateRequest request = createRequest(false);
        createProduct().execute(storeId, EMAIL, request);

        InOrder order = inOrder(storeAccess, storeService, planEntitlements, productCatalog);
        order.verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_CREATE);
        order.verify(storeService).requireExists(storeId);
        order.verify(storeService).lockForUpdate(storeId);
        order.verify(planEntitlements).requireCapacity(storeId, PlanEntitlements.Capacity.PRODUCTS, 1);
        order.verify(productCatalog).create(storeId, request, List.of(), true);
        verify(storeAccess, never()).requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_PUBLISH);
        verify(storeAccess, never()).requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_IMAGE_MANAGE);
        verify(planEntitlements, never()).isSubscriptionActive(any());
    }

    @Test
    void createWithoutPermissionTouchesNothingElse() {
        doThrow(new AccessDeniedException("denied"))
                .when(storeAccess).requirePermission(anyString(), any(), anyString());

        assertThrows(AccessDeniedException.class, () -> createProduct().execute(storeId, EMAIL, createRequest(false)));

        verifyNoInteractions(storeService, planEntitlements, productCatalog);
    }

    @Test
    void createWithPublishWithoutPublishPermissionCreatesNothing() {
        deny(StorePermissionCodes.PRODUCT_PUBLISH);

        assertThrows(AccessDeniedException.class, () -> createProduct().execute(storeId, EMAIL, createRequest(true)));

        verifyNoInteractions(storeService, planEntitlements, productCatalog);
    }

    @Test
    void createWithImagesWithoutImagePermissionCreatesNothing() {
        deny(StorePermissionCodes.PRODUCT_IMAGE_MANAGE);

        assertThrows(AccessDeniedException.class,
                () -> createProduct().execute(storeId, EMAIL, createRequest(false), oneImage()));

        verifyNoInteractions(storeService, planEntitlements, productCatalog);
    }

    @Test
    void createAndPublishInTheSameRequestPassesTheSubscriptionState() {
        ProductCreateRequest request = createRequest(true);
        List<MultipartFile> images = oneImage();
        when(planEntitlements.isSubscriptionActive(storeId)).thenReturn(false);

        createProduct().execute(storeId, EMAIL, request, images);

        verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_CREATE);
        verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_IMAGE_MANAGE);
        verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_PUBLISH);
        verify(productCatalog).create(storeId, request, images, false);
    }

    @Test
    void createOverPlanCapacityCreatesNothing() {
        doThrow(new ConflictException("SELLER_PLAN_LIMIT_REACHED", "limite"))
                .when(planEntitlements).requireCapacity(storeId, PlanEntitlements.Capacity.PRODUCTS, 1);

        assertThrows(ConflictException.class,
                () -> createProduct().execute(storeId, EMAIL, createRequest(false), oneImage()));

        verifyNoInteractions(productCatalog);
    }

    @Test
    void updateOnlyGalleryNeedsImagePermissionNotUpdate() {
        ProductUpdateRequest request = new ProductUpdateRequest(null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, Set.of(UUID.randomUUID()), null, null);

        updateProduct().execute(storeId, branchId, productId, EMAIL, request, List.of());

        verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_IMAGE_MANAGE);
        verify(storeAccess, never()).requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_UPDATE);
        verify(productCatalog).update(storeId, branchId, productId, request, List.of(), true);
    }

    @Test
    void updateWithPublishWithoutPublishPermissionChangesNothing() {
        ProductUpdateRequest request = new ProductUpdateRequest("Nuevo", null, null, null, null, null, null, null,
                null, null, null, null, null, true, null, null, null);
        deny(StorePermissionCodes.PRODUCT_PUBLISH);

        assertThrows(AccessDeniedException.class,
                () -> updateProduct().execute(storeId, branchId, productId, EMAIL, request, List.of()));

        verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_UPDATE);
        verifyNoInteractions(productCatalog);
    }

    @Test
    void publishingChecksRequirementsWithTheSubscriptionState() {
        when(planEntitlements.isSubscriptionActive(storeId)).thenReturn(true);

        new PublishProduct(storeAccess, storeService, planEntitlements, productCatalog)
                .execute(storeId, productId, EMAIL, new ProductPublicationRequest(true));

        InOrder order = inOrder(storeAccess, storeService, planEntitlements, productCatalog);
        order.verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_PUBLISH);
        order.verify(storeService).requireExists(storeId);
        order.verify(planEntitlements).isSubscriptionActive(storeId);
        order.verify(productCatalog).publish(storeId, productId, true);
    }

    @Test
    void unpublishingDoesNotRequireSubscription() {
        new PublishProduct(storeAccess, storeService, planEntitlements, productCatalog)
                .execute(storeId, productId, EMAIL, new ProductPublicationRequest(false));

        verify(planEntitlements, never()).isSubscriptionActive(any());
        verify(productCatalog, never()).publish(any(), any(), anyBoolean());
        verify(productCatalog).unpublish(storeId, productId);
    }

    @Test
    void deleteRequiresProductDeletePermission() {
        new DeleteProduct(storeAccess, storeService, productCatalog).execute(storeId, productId, EMAIL);

        InOrder order = inOrder(storeAccess, storeService, productCatalog);
        order.verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_DELETE);
        order.verify(storeService).requireExists(storeId);
        order.verify(productCatalog).delete(storeId, productId);
    }

    @Test
    void imageOperationsRequireImageManagePermission() {
        UUID imageId = UUID.randomUUID();
        new ManageProductImages(storeAccess, storeService, productImages)
                .delete(storeId, productId, imageId, EMAIL);

        InOrder order = inOrder(storeAccess, storeService, productImages);
        order.verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_IMAGE_MANAGE);
        order.verify(storeService).requireExists(storeId);
        order.verify(productImages).delete(eq(storeId), eq(productId), eq(imageId));
    }
}
