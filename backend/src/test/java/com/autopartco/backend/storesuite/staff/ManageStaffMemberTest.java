package com.autopartco.backend.storesuite.staff;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.billing.PlanEntitlements.Capacity;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.organization.StaffDirectory;
import com.autopartco.backend.core.organization.StaffInvitations;
import com.autopartco.backend.core.organization.StaffRoles;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.shared.error.BadRequestException;

@ExtendWith(MockitoExtension.class)
class ManageStaffMemberTest {
    @Mock
    private StoreAccess storeAccess;
    @Mock
    private StoreService storeService;
    @Mock
    private AccountLookup accountLookup;
    @Mock
    private StaffDirectory staffDirectory;
    @Mock
    private StaffRoles staffRoles;
    @Mock
    private StaffInvitations staffInvitations;
    @Mock
    private PlanEntitlements planEntitlements;

    @InjectMocks
    private ManageStaffMember manageStaffMember;

    private final UUID storeId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final AccountRef admin = new AccountRef(
            UUID.randomUUID(), UUID.randomUUID(), "admin@example.com", "Ana", "ACTIVE", null, "Ana");

    @Test
    void requiresManagePermissionBeforeAnything() {
        doThrow(new AccessDeniedException("forbidden")).when(storeAccess)
                .requirePermission("admin@example.com", storeId, StorePermissionCodes.COLLABORATOR_MANAGE);

        assertThrows(AccessDeniedException.class,
                () -> manageStaffMember.changeRole(storeId, memberId, "admin@example.com", "SALES"));
        assertThrows(AccessDeniedException.class,
                () -> manageStaffMember.setActive(storeId, memberId, "admin@example.com", false));
        verifyNoInteractions(storeService, staffDirectory, staffRoles, planEntitlements);
    }

    @Test
    void rejectsTheOwnerRoleWithoutTouchingTheStore() {
        assertThrows(BadRequestException.class,
                () -> manageStaffMember.changeRole(storeId, memberId, "admin@example.com", "OWNER"));
        verifyNoInteractions(storeService, staffDirectory, staffRoles);
    }

    @Test
    void changeRoleLocksTheStoreEnsuresTheRoleAndAssignsIt() {
        UUID roleId = UUID.randomUUID();
        when(staffRoles.ensure(storeId, "SALES")).thenReturn(roleId);
        when(accountLookup.requireByEmail("admin@example.com")).thenReturn(admin);

        manageStaffMember.changeRole(storeId, memberId, "Admin@Example.com", "SALES");

        InOrder order = inOrder(storeService, staffRoles, staffDirectory);
        order.verify(storeService).lockForUpdate(storeId);
        order.verify(staffRoles).ensure(storeId, "SALES");
        order.verify(staffDirectory).changeRole(storeId, memberId, admin.personId(), roleId);
    }

    @Test
    void reactivateChecksTheUserSeatFirst() {
        when(staffInvitations.countPending(storeId)).thenReturn(2L);
        when(accountLookup.requireByEmail("admin@example.com")).thenReturn(admin);

        manageStaffMember.setActive(storeId, memberId, "admin@example.com", true);

        InOrder order = inOrder(storeService, planEntitlements, staffDirectory);
        order.verify(storeService).lockForUpdate(storeId);
        order.verify(planEntitlements).requireCapacity(storeId, Capacity.USERS, 3L);
        order.verify(staffDirectory).setActive(storeId, memberId, admin.personId(), true);
    }
}
