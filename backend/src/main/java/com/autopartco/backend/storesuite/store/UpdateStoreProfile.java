package com.autopartco.backend.storesuite.store;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreResponse;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.core.organization.StoreUpdateRequest;

import lombok.AllArgsConstructor;

/** Actualiza nombre, NIT o correo del almacén. Requiere {@code STORE_UPDATE}. */
@Service
@AllArgsConstructor
public class UpdateStoreProfile {
    private final StoreAccess storeAccess;
    private final StoreService storeService;

    @Transactional
    public StoreResponse execute(UUID storeId, String email, StoreUpdateRequest request) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_UPDATE);
        return storeService.update(storeId, request);
    }
}
