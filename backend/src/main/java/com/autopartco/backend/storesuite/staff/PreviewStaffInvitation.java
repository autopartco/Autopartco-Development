package com.autopartco.backend.storesuite.staff;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.organization.StaffInvitationView;
import com.autopartco.backend.core.organization.StaffInvitations;

import lombok.AllArgsConstructor;

/** Vista previa pública de una invitación pendiente a partir del token del enlace. */
@Service
@AllArgsConstructor
public class PreviewStaffInvitation {
    private final StaffInvitations staffInvitations;
    private final AccountLookup accountLookup;

    @Transactional(readOnly = true)
    public StaffResponse.InvitationPreview execute(String rawToken) {
        StaffInvitationView invitation = staffInvitations.findPendingByToken(rawToken);
        boolean accountExists = accountLookup.existsByEmail(invitation.email());
        return new StaffResponse.InvitationPreview(
                invitation.storeId(),
                invitation.storeName(),
                invitation.email(),
                invitation.roleName(),
                invitation.permissionCodes(),
                !accountExists,
                invitation.expiresAt()
        );
    }
}
