package com.autopartco.backend.storesuite.products;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;

import lombok.AllArgsConstructor;

/** Lista los productos (por sucursal) del almacén. Requiere {@code PRODUCT_READ}. */
@Service
@AllArgsConstructor
public class ListStoreProducts {
    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final ListingQueries listingQueries;

    @Transactional(readOnly = true)
    public List<ProductResponse> execute(UUID storeId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_READ);
        storeService.requireExists(storeId);
        return listingQueries.storeListings(storeId);
    }
}
