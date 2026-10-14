package com.autopartco.backend.storesuite.staff;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.PermissionOption;
import com.autopartco.backend.core.organization.StaffDirectory;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;

import lombok.AllArgsConstructor;

/** Permisos que se pueden asignar a un invitado. Requiere {@code COLLABORATOR_MANAGE}. */
@Service
@AllArgsConstructor
public class ListAssignablePermissions {
    private final StoreAccess storeAccess;
    private final StaffDirectory staffDirectory;

    @Transactional(readOnly = true)
    public List<PermissionOption> execute(UUID storeId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.COLLABORATOR_MANAGE);
        return staffDirectory.assignablePermissions();
    }
}
