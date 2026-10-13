package com.autopartco.backend.storesuite.staff;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.organization.StaffDirectory;
import com.autopartco.backend.core.organization.StaffInvitations;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;

@ExtendWith(MockitoExtension.class)
class InviteStaffMemberTest {
    @Mock
    private StoreAccess storeAccess;
    @Mock
    private StoreService storeService;
    @Mock
    private AccountLookup accountLookup;
    @Mock
    private StaffDirectory staffDirectory;
    @Mock
    private StaffInvitations staffInvitations;
    @Mock
    private StaffSeats staffSeats;
    @Mock
    private StaffInvitationMailer mailer;

    @InjectMocks
    private InviteStaffMember inviteStaffMember;

    @Test
    void requiresManagementPermissionBeforeCreatingAnInvitation() {
        UUID storeId = UUID.randomUUID();
        doThrow(new AccessDeniedException("forbidden"))
                .when(storeAccess)
                .requirePermission("owner@example.com", storeId, StorePermissionCodes.COLLABORATOR_MANAGE);

        assertThrows(
                AccessDeniedException.class,
                () -> inviteStaffMember.execute(storeId, "owner@example.com", null)
        );
        verify(storeAccess).requirePermission(
                "owner@example.com",
                storeId,
                StorePermissionCodes.COLLABORATOR_MANAGE
        );
        verifyNoInteractions(
                storeService,
                accountLookup,
                staffDirectory,
                staffInvitations,
                staffSeats,
                mailer
        );
    }
}
