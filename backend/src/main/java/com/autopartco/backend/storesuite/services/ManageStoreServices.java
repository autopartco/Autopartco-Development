package com.autopartco.backend.storesuite.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreServiceRequest;
import com.autopartco.backend.core.organization.StoreServiceView;
import com.autopartco.backend.core.organization.StoreServices;

import lombok.AllArgsConstructor;

/** Servicios del almacén. Leer exige {@code STORE_READ}; crear, reemplazar y desactivar, {@code STORE_UPDATE}. */
@Service
@AllArgsConstructor
public class ManageStoreServices {
    private final StoreAccess storeAccess;
    private final StoreServices storeServices;

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
        return storeServices.create(storeId, request);
    }

    @Transactional
    public StoreServiceView update(UUID storeId, UUID serviceId, String email, StoreServiceRequest request) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_UPDATE);
        return storeServices.update(storeId, serviceId, request);
    }

    @Transactional
    public void deactivate(UUID storeId, UUID serviceId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_UPDATE);
        storeServices.deactivate(storeId, serviceId);
    }
}
