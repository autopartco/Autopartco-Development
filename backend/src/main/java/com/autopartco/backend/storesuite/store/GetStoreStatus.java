package com.autopartco.backend.storesuite.store;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.core.organization.StoreStatusResponse;

import lombok.AllArgsConstructor;

/** Estado del almacén. Requiere {@code STORE_READ}. */
@Service
@AllArgsConstructor
public class GetStoreStatus {
    private final StoreAccess storeAccess;
    private final StoreService storeService;

    @Transactional(readOnly = true)
    public StoreStatusResponse execute(UUID storeId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_READ);
        return storeService.status(storeId);
    }
}
