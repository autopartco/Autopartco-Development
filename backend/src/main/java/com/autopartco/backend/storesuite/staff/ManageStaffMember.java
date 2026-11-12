package com.autopartco.backend.storesuite.staff;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.billing.PlanEntitlements.Capacity;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.organization.StaffDirectory;
import com.autopartco.backend.core.organization.StaffInvitations;
import com.autopartco.backend.core.organization.StaffRoles;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;

import lombok.AllArgsConstructor;

/**
 * Cambio de rol, desactivación y reactivación de un colaborador. Requiere {@code COLLABORATOR_MANAGE}; nunca actúa
 * sobre el propietario ni sobre quien hace la petición. Reactivar vuelve a ocupar cupo de usuarios del plan.
 */
@Service
@AllArgsConstructor
public class ManageStaffMember {
    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final AccountLookup accountLookup;
    private final StaffDirectory staffDirectory;
    private final StaffRoles staffRoles;
    private final StaffInvitations staffInvitations;
    private final PlanEntitlements planEntitlements;

    @Transactional
    public void changeRole(UUID storeId, UUID memberId, String email, String roleCode) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.COLLABORATOR_MANAGE);
        StaffRoles.require(roleCode);
        storeService.lockForUpdate(storeId);
        UUID roleId = staffRoles.ensure(storeId, roleCode);
        staffDirectory.changeRole(storeId, memberId, actorPersonId(email), roleId);
    }

    @Transactional
    public boolean setActive(UUID storeId, UUID memberId, String email, boolean active) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.COLLABORATOR_MANAGE);
        storeService.lockForUpdate(storeId);
        if (active) {
            planEntitlements.requireCapacity(storeId, Capacity.USERS, staffInvitations.countPending(storeId) + 1);
        }
        return staffDirectory.setActive(storeId, memberId, actorPersonId(email), active);
    }

    private UUID actorPersonId(String email) {
        return accountLookup.requireByEmail(email.trim().toLowerCase(Locale.ROOT)).personId();
    }
}
