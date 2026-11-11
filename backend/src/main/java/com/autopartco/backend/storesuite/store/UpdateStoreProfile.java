package com.autopartco.backend.storesuite.store;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreResponse;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.core.organization.StoreUpdateRequest;
import com.autopartco.backend.shared.events.StoreProfileChanged;

import lombok.AllArgsConstructor;

/** Actualiza nombre, NIT o correo del almacén. Requiere {@code STORE_UPDATE}. Invalida el perfil público cacheado. */
@Service
@AllArgsConstructor
public class UpdateStoreProfile {
    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final ApplicationEventPublisher events;

    @Transactional
    public StoreResponse execute(UUID storeId, String email, StoreUpdateRequest request) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_UPDATE);
        StoreResponse updated = storeService.update(storeId, request);
        events.publishEvent(new StoreProfileChanged(storeId));
        return updated;
    }
}
