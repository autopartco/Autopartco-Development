package com.autopartco.backend.storesuite.store;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.StoreAccess;

import lombok.AllArgsConstructor;

/** Permisos de la cuenta autenticada en el almacén; vacío si no colabora o la solicitud no está activa. */
@Service
@AllArgsConstructor
public class ListMyStorePermissions {
    private final StoreAccess storeAccess;

    @Transactional(readOnly = true)
    public List<String> execute(UUID storeId, String email) {
        return storeAccess.permissionsFor(email, storeId);
    }
}
