package com.autopartco.backend.storesuite.staff;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.StaffInvitations;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;

import lombok.AllArgsConstructor;

/** Revoca una invitación sin aceptar. Requiere {@code COLLABORATOR_MANAGE}. */
@Service
@AllArgsConstructor
public class RevokeStaffInvitation {
    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final StaffInvitations staffInvitations;

    @Transactional
    public void execute(UUID storeId, UUID invitationId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.COLLABORATOR_MANAGE);
        storeService.lockForUpdate(storeId);
        staffInvitations.revoke(storeId, invitationId);
    }
}
