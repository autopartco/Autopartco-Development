package com.autopartco.backend.storesuite.products;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.catalog.ProductCatalog;
import com.autopartco.backend.core.catalog.ProductListingRequest;
import com.autopartco.backend.core.catalog.StoreProductDetail;
import com.autopartco.backend.core.catalog.internal.Product;
import com.autopartco.backend.core.catalog.internal.ProductRepository;
import com.autopartco.backend.core.inventory.StockService;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

@ExtendWith(MockitoExtension.class)
class ProductListingsTest {
    private static final String EMAIL = "user@example.com";

    private final UUID storeId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();

    @Mock
    private StoreAccess storeAccess;

    @Mock
    private StoreService storeService;

    @Mock
    private PlanEntitlements planEntitlements;

    @Mock
    private ProductCatalog productCatalog;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BranchService branchService;

    @Mock
    private StockService stockService;

    @Test
    void detailRequiresProductReadBeforeLoading() {
        doThrow(new AccessDeniedException("forbidden"))
                .when(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_READ);

        assertThrows(AccessDeniedException.class,
                () -> new GetStoreProduct(storeAccess, storeService, productCatalog).execute(storeId, productId, EMAIL));
        verifyNoInteractions(productCatalog);
    }

    @Test
    void addListingChecksPermissionAndActiveSubscriptionBeforeWriting() {
        ProductListingRequest request = new ProductListingRequest(branchId, new BigDecimal("85000.00"), 5);
        StoreProductDetail detail = mock(StoreProductDetail.class);
        when(productCatalog.addListing(storeId, productId, request)).thenReturn(detail);

        StoreProductDetail result = new AddProductListing(storeAccess, storeService, planEntitlements, productCatalog)
                .execute(storeId, productId, EMAIL, request);

        assertEquals(detail, result);
        InOrder order = inOrder(storeAccess, planEntitlements, productCatalog);
        order.verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_UPDATE);
        order.verify(planEntitlements).requireSubscriptionActive(storeId);
        order.verify(productCatalog).addListing(storeId, productId, request);
    }

    @Test
    void addListingIsBlockedWhenSubscriptionExpired() {
        ProductListingRequest request = new ProductListingRequest(branchId, new BigDecimal("10.00"), 0);
        doThrow(new ConflictException("SELLER_SUBSCRIPTION_EXPIRED", "vencida"))
                .when(planEntitlements).requireSubscriptionActive(storeId);

        ConflictException error = assertThrows(ConflictException.class,
                () -> new AddProductListing(storeAccess, storeService, planEntitlements, productCatalog)
                        .execute(storeId, productId, EMAIL, request));

        assertEquals("SELLER_SUBSCRIPTION_EXPIRED", error.getCode());
        verify(productCatalog, never()).addListing(any(), any(), any());
    }

    @Test
    void removeListingRequiresProductUpdate() {
        doThrow(new AccessDeniedException("forbidden"))
                .when(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_UPDATE);

        assertThrows(AccessDeniedException.class,
                () -> new RemoveProductListing(storeAccess, storeService, productCatalog)
                        .execute(storeId, productId, branchId, EMAIL));
        verifyNoInteractions(productCatalog);
    }

    @Test
    void removingTheLastListingIsConflict() {
        ProductCatalog catalog = catalog();
        when(productRepository.lockOwned(storeId, productId)).thenReturn(Optional.of(mock(Product.class)));
        when(stockService.countListingsOfProduct(productId)).thenReturn(0L);

        ConflictException error = assertThrows(ConflictException.class,
                () -> catalog.removeListing(storeId, productId, branchId));

        assertEquals("LAST_LISTING", error.getCode());
        verify(stockService).removeListing(storeId, branchId, productId);
    }

    @Test
    void removingAListingKeepsTheOthers() {
        ProductCatalog catalog = catalog();
        when(productRepository.lockOwned(storeId, productId)).thenReturn(Optional.of(mock(Product.class)));
        when(stockService.countListingsOfProduct(productId)).thenReturn(1L);

        catalog.removeListing(storeId, productId, branchId);

        verify(stockService).removeListing(storeId, branchId, productId);
    }

    @Test
    void listingOnProductOfAnotherStoreIsNotFound() {
        ProductCatalog catalog = catalog();
        when(productRepository.lockOwned(storeId, productId)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(NotFoundException.class,
                () -> catalog.addListing(storeId, productId,
                        new ProductListingRequest(branchId, new BigDecimal("10.00"), 1)));

        assertEquals("PRODUCT_NOT_FOUND", error.getCode());
        verifyNoInteractions(stockService);
    }

    @Test
    void addListingValidatesActiveBranchBeforeCreating() {
        ProductCatalog catalog = catalog();
        when(productRepository.lockOwned(storeId, productId)).thenReturn(Optional.of(mock(Product.class)));
        doThrow(new NotFoundException("BRANCH_NOT_FOUND", "no"))
                .when(branchService).requireActiveInStore(storeId, branchId);

        NotFoundException error = assertThrows(NotFoundException.class,
                () -> catalog.addListing(storeId, productId,
                        new ProductListingRequest(branchId, new BigDecimal("10.00"), 1)));

        assertEquals("BRANCH_NOT_FOUND", error.getCode());
        verify(stockService, never()).addListing(any(), any(), any(), any());
    }

    private ProductCatalog catalog() {
        return new ProductCatalog(productRepository, null, null, null, null, branchService, stockService, null, null);
    }
}
