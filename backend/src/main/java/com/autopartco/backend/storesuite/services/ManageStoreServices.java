package com.autopartco.backend.storesuite.services;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreServiceRequest;
import com.autopartco.backend.core.organization.StoreServiceView;
import com.autopartco.backend.core.organization.StoreServices;
import com.autopartco.backend.shared.events.StoreProfileChanged;

import lombok.AllArgsConstructor;

/**
 * Servicios del almacén. Leer exige {@code STORE_READ}; crear, reemplazar y desactivar, {@code STORE_UPDATE}. Las
 * escrituras invalidan el perfil y las sucursales públicas cacheadas ({@link StoreProfileChanged}).
 */
@Service
@AllArgsConstructor
public class ManageStoreServices {
    private final StoreAccess storeAccess;
    private final StoreServices storeServices;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<StoreServiceView> list(UUID storeId, String email, boolean includeInactive) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_READ);
        return storeServices.list(storeId, includeInactive);
    }

    @Transactional(readOnly = true)
    public StoreServiceView get(UUID storeId, UUID serviceId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_READ);
        return storeServices.get(storeId, serviceId);
    }

    @Transactional
    public StoreServiceView create(UUID storeId, String email, StoreServiceRequest request) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_UPDATE);
        StoreServiceView created = storeServices.create(storeId, request);
        events.publishEvent(new StoreProfileChanged(storeId));
        return created;
    }

    @Transactional
    public StoreServiceView update(UUID storeId, UUID serviceId, String email, StoreServiceRequest request) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_UPDATE);
        StoreServiceView updated = storeServices.update(storeId, serviceId, request);
        events.publishEvent(new StoreProfileChanged(storeId));
        return updated;
    }

    @Transactional
    public void deactivate(UUID storeId, UUID serviceId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_UPDATE);
        storeServices.deactivate(storeId, serviceId);
        events.publishEvent(new StoreProfileChanged(storeId));
    }
}
