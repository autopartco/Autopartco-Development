package com.autopartco.backend.product.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.catalog.ProductCatalog;
import com.autopartco.backend.core.catalog.ProductCreateRequest;
import com.autopartco.backend.core.catalog.ProductOptionsResponse;
import com.autopartco.backend.core.catalog.ProductPublicationRequest;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.catalog.ProductUpdateRequest;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;

import lombok.AllArgsConstructor;

/**
 * Temporal (fase 4a): permiso y derechos de plan antes de {@code core.catalog}. En la fase 4b pasa a los casos de
 * uso de {@code storesuite.products} y {@code marketplace.catalog}.
 */
@Service
@AllArgsConstructor
public class ProductService {
    private final ProductCatalog productCatalog;
    private final ListingQueries listingQueries;
    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final PlanEntitlements entitlementService;

    @Transactional(readOnly = true)
    public List<ProductResponse> list(UUID storeId, String email) {
        requirePermission(storeId, email, StorePermissionCodes.PRODUCT_READ);
        return listingQueries.storeListings(storeId);
    }

    @Transactional
    public ProductResponse create(UUID storeId, String email, ProductCreateRequest request) {
        requirePermission(storeId, email, StorePermissionCodes.PRODUCT_CREATE);
        storeService.lockForUpdate(storeId);
        entitlementService.requireCapacity(storeId, PlanEntitlements.Capacity.PRODUCTS, 1);
        return productCatalog.create(storeId, request);
    }

    @Transactional
    public ProductResponse update(
            UUID storeId,
            UUID branchId,
            UUID productId,
            String email,
            ProductUpdateRequest request
    ) {
        requirePermission(storeId, email, StorePermissionCodes.PRODUCT_UPDATE);
        return productCatalog.update(storeId, branchId, productId, request);
    }

    @Transactional
    public void publish(
            UUID storeId,
            UUID productId,
            String email,
            ProductPublicationRequest request
    ) {
        requirePermission(storeId, email, StorePermissionCodes.PRODUCT_PUBLISH);
        if (request.published()) {
            entitlementService.requireSubscriptionActive(storeId);
        }
        productCatalog.setPublished(storeId, productId, request.published());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> published() {
        return listingQueries.publishedListings();
    }

    @Transactional(readOnly = true)
    public ProductResponse published(UUID productId) {
        return listingQueries.publishedListing(productId);
    }

    @Transactional(readOnly = true)
    public ProductOptionsResponse options() {
        return listingQueries.options();
    }

    @Transactional(readOnly = true)
    public List<ProductOptionsResponse.ManufacturerOption> manufacturers() {
        return listingQueries.manufacturers();
    }

    @Transactional(readOnly = true)
    public List<ProductOptionsResponse.CategoryOption> categories() {
        return listingQueries.categories();
    }

    @Transactional(readOnly = true)
    public List<ProductOptionsResponse.CompatibilityOption> compatibilities() {
        return listingQueries.compatibilities();
    }

    private void requirePermission(UUID storeId, String email, String permissionCode) {
        storeAccess.requirePermission(email, storeId, permissionCode);
        storeService.requireExists(storeId);
    }
}
