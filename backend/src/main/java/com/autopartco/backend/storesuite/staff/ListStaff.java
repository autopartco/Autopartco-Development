package com.autopartco.backend.storesuite.staff;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.StaffDirectory;
import com.autopartco.backend.core.organization.StaffInvitations;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;

import lombok.AllArgsConstructor;

/** Colaboradores e invitaciones abiertas del almacén. Requiere {@code COLLABORATOR_READ}. */
@Service
@AllArgsConstructor
public class ListStaff {
    private final StoreAccess storeAccess;
    private final StaffDirectory staffDirectory;
    private final StaffInvitations staffInvitations;

    @Transactional(readOnly = true)
    public StaffResponse execute(UUID storeId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.COLLABORATOR_READ);
        return new StaffResponse(
                storeId,
                staffDirectory.listMembers(storeId).stream().map(StaffResponse.Member::from).toList(),
                staffInvitations.listOpen(storeId).stream().map(StaffInvitationResponse::from).toList()
        );
    }
}
