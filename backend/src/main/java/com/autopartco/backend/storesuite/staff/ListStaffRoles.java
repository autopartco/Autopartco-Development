package com.autopartco.backend.storesuite.staff;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.StaffRoles;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;

import lombok.AllArgsConstructor;

/** Roles predefinidos que se pueden asignar a un colaborador. Requiere {@code COLLABORATOR_READ}. */
@Service
@AllArgsConstructor
public class ListStaffRoles {
    private final StoreAccess storeAccess;

    @Transactional(readOnly = true)
    public List<StaffRoleResponse> execute(UUID storeId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.COLLABORATOR_READ);
        return StaffRoles.DEFINITIONS.stream().map(StaffRoleResponse::from).toList();
    }
}
