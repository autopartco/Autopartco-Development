package com.autopartco.backend.storesuite.products;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.catalog.ProductCatalog;
import com.autopartco.backend.core.catalog.ProductCreateRequest;
import com.autopartco.backend.core.catalog.ProductImages;
import com.autopartco.backend.core.catalog.ProductPublicationRequest;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;

/** Orden de chequeos de los casos de uso de productos del almacén (permiso, almacén, plan, core). */
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

    @Test
    void createChecksPermissionStoreLockAndCapacityBeforeCreating() {
        ProductCreateRequest request = null;
        new CreateProduct(storeAccess, storeService, planEntitlements, productCatalog)
                .execute(storeId, EMAIL, request);

        InOrder order = inOrder(storeAccess, storeService, planEntitlements, productCatalog);
        order.verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_CREATE);
        order.verify(storeService).requireExists(storeId);
        order.verify(storeService).lockForUpdate(storeId);
        order.verify(planEntitlements).requireCapacity(storeId, PlanEntitlements.Capacity.PRODUCTS, 1);
        order.verify(productCatalog).create(storeId, request);
    }

    @Test
    void createWithoutPermissionTouchesNothingElse() {
        doThrow(new AccessDeniedException("denied"))
                .when(storeAccess).requirePermission(anyString(), any(), anyString());

        assertThrows(AccessDeniedException.class, () ->
                new CreateProduct(storeAccess, storeService, planEntitlements, productCatalog)
                        .execute(storeId, EMAIL, null));

        verifyNoInteractions(storeService, planEntitlements, productCatalog);
    }

    @Test
    void publishingRequiresActiveSubscription() {
        new PublishProduct(storeAccess, storeService, planEntitlements, productCatalog)
                .execute(storeId, productId, EMAIL, new ProductPublicationRequest(true));

        InOrder order = inOrder(storeAccess, storeService, planEntitlements, productCatalog);
        order.verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_PUBLISH);
        order.verify(storeService).requireExists(storeId);
        order.verify(planEntitlements).requireSubscriptionActive(storeId);
        order.verify(productCatalog).setPublished(storeId, productId, true);
    }

    @Test
    void unpublishingDoesNotRequireSubscription() {
        new PublishProduct(storeAccess, storeService, planEntitlements, productCatalog)
                .execute(storeId, productId, EMAIL, new ProductPublicationRequest(false));

        verify(planEntitlements, never()).requireSubscriptionActive(any());
        verify(productCatalog).setPublished(storeId, productId, false);
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
        order.verify(productImages).delete(storeId, productId, imageId);
    }
}
