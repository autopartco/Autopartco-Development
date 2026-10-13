package com.autopartco.backend.storesuite.staff;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.StaffInvitationView;
import com.autopartco.backend.core.organization.StaffInvitations;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;

import lombok.AllArgsConstructor;

/**
 * Reenvía una invitación abierta con un token nuevo y 7 días más de vigencia. Si ya había vencido vuelve a
 * ocupar cupo de usuarios. Requiere {@code COLLABORATOR_MANAGE}.
 */
@Service
@AllArgsConstructor
public class ResendStaffInvitation {
    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final StaffInvitations staffInvitations;
    private final StaffSeats staffSeats;
    private final StaffInvitationMailer mailer;

    @Transactional
    public StaffInvitationResponse execute(UUID storeId, UUID invitationId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.COLLABORATOR_MANAGE);
        storeService.lockForUpdate(storeId);
        StaffInvitationView invitation = staffInvitations.requireOpen(storeId, invitationId);
        LocalDateTime now = LocalDateTime.now();
        staffSeats.reserve(storeId, invitation.expiresAt().isAfter(now) ? 0 : 1);
        StaffInvitations.Issued issued = staffInvitations.renew(storeId, invitationId, now);
        mailer.send(issued);
        return StaffInvitationResponse.from(issued.invitation());
    }
}
