package com.autopartco.backend.storesuite.products;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.autopartco.backend.core.catalog.ManufacturerSearchResult;
import com.autopartco.backend.core.catalog.StoreManufacturerRequest;
import com.autopartco.backend.core.catalog.StoreManufacturerResponse;
import com.autopartco.backend.core.catalog.StoreManufacturers;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;

import lombok.AllArgsConstructor;

/** Lista, busca y sugiere fabricantes en nombre del almacén. Requiere {@code PRODUCT_CREATE}. */
@Service
@AllArgsConstructor
public class ManageStoreManufacturers {
    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final StoreManufacturers storeManufacturers;

    public List<StoreManufacturerResponse> list(UUID storeId, String email) {
        authorize(storeId, email);
        return storeManufacturers.list(storeId);
    }

    public List<ManufacturerSearchResult> search(UUID storeId, String email, String q) {
        authorize(storeId, email);
        return storeManufacturers.search(storeId, q);
    }

    public StoreManufacturers.Suggestion suggest(UUID storeId, String email, StoreManufacturerRequest request) {
        authorize(storeId, email);
        return storeManufacturers.suggest(storeId, request);
    }

    private void authorize(UUID storeId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.PRODUCT_CREATE);
        storeService.requireExists(storeId);
    }
}
