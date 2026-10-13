package com.autopartco.backend.storesuite.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountProvisioning;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.organization.StaffDirectory;
import com.autopartco.backend.core.organization.StaffInvitationView;
import com.autopartco.backend.core.organization.StaffInvitations;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;

@ExtendWith(MockitoExtension.class)
class AcceptStaffInvitationTest {
    @Mock
    private StaffInvitations staffInvitations;
    @Mock
    private StaffSeats staffSeats;
    @Mock
    private AccountLookup accountLookup;
    @Mock
    private AccountProvisioning accountProvisioning;
    @Mock
    private StaffDirectory staffDirectory;

    @InjectMocks
    private AcceptStaffInvitation acceptStaffInvitation;

    private final StaffInvitationView invitation = new StaffInvitationView(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Repuestos Ana",
            "nuevo@example.com",
            UUID.randomUUID(),
            "Vendedor",
            List.of("STORE_READ"),
            LocalDateTime.now().plusDays(1),
            LocalDateTime.now()
    );

    @Test
    void requiresAccountDetailsWhenTheInvitedEmailHasNoAccount() {
        when(staffInvitations.lockPendingByToken("token")).thenReturn(invitation);
        when(accountLookup.lockByEmail("nuevo@example.com")).thenReturn(Optional.empty());

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> acceptStaffInvitation.execute(new InvitationAcceptRequest("token", null))
        );

        assertEquals("SELLER_COLLABORATOR_ACCOUNT_REQUIRED", exception.getCode());
        verifyNoInteractions(accountProvisioning, staffDirectory);
        verify(staffInvitations, never()).markAccepted(any());
    }

    @Test
    void rejectsALockedAccountWithoutAddingTheCollaborator() {
        when(staffInvitations.lockPendingByToken("token")).thenReturn(invitation);
        when(accountLookup.lockByEmail("nuevo@example.com")).thenReturn(Optional.of(account("LOCKED")));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> acceptStaffInvitation.execute(new InvitationAcceptRequest("token", null))
        );

        assertEquals("SELLER_COLLABORATOR_ACCOUNT_LOCKED", exception.getCode());
        verifyNoInteractions(accountProvisioning, staffDirectory);
        verify(staffInvitations, never()).markAccepted(any());
    }

    @Test
    void activatesAPendingAccountThenAddsTheCollaboratorAndConsumesTheInvitation() {
        AccountRef pending = account("PENDING");
        when(staffInvitations.lockPendingByToken("token")).thenReturn(invitation);
        when(accountLookup.lockByEmail("nuevo@example.com")).thenReturn(Optional.of(pending));

        StaffResponse.Accepted accepted = acceptStaffInvitation.execute(new InvitationAcceptRequest("token", null));

        InOrder order = inOrder(staffInvitations, staffSeats, accountProvisioning, staffDirectory);
        order.verify(staffInvitations).lockPendingByToken("token");
        order.verify(staffSeats).reserve(invitation.storeId(), 0);
        order.verify(accountProvisioning).activate(pending.accountId());
        order.verify(staffDirectory).addCollaborator(invitation.storeId(), pending.personId(), invitation.roleId());
        order.verify(staffInvitations).markAccepted(invitation.id());
        assertEquals(new StaffResponse.Accepted(
                invitation.storeId(),
                "Repuestos Ana",
                "nuevo@example.com",
                "Vendedor"
        ), accepted);
    }

    private AccountRef account(String stateCode) {
        return new AccountRef(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "nuevo@example.com",
                "Ana Pérez",
                stateCode,
                "CUSTOMER",
                "Ana"
        );
    }
}
