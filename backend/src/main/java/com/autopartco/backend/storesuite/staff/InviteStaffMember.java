package com.autopartco.backend.storesuite.staff;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.organization.StaffDirectory;
import com.autopartco.backend.core.organization.StaffInvitations;
import com.autopartco.backend.core.organization.StaffRoles;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.shared.error.ConflictException;

import lombok.AllArgsConstructor;

/**
 * Invita a un colaborador con un rol predefinido ({@link StaffRoles}). Requiere {@code COLLABORATOR_MANAGE} y cupo
 * de usuarios en el plan. Bloquea el almacén antes de contar el cupo.
 */
@Service
@AllArgsConstructor
public class InviteStaffMember {
    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final AccountLookup accountLookup;
    private final StaffDirectory staffDirectory;
    private final StaffInvitations staffInvitations;
    private final StaffRoles staffRoles;
    private final StaffSeats staffSeats;
    private final StaffInvitationMailer mailer;

    @Transactional
    public StaffInvitationResponse execute(UUID storeId, String email, StaffInviteRequest request) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.COLLABORATOR_MANAGE);
        storeService.lockForUpdate(storeId);
        StaffRoles.require(request.roleCode());
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (accountLookup.findByEmail(normalizedEmail)
                .map(account -> staffDirectory.isMember(account.personId(), storeId))
                .orElse(false)) {
            throw new ConflictException(
                    "SELLER_COLLABORATOR_ALREADY_EXISTS",
                    "Esta persona ya es colaboradora del almacén. Si está desactivada, reactívala desde la lista."
            );
        }
        staffInvitations.requireNoneOpenFor(storeId, normalizedEmail);
        staffSeats.reserve(storeId, 1);
        UUID roleId = staffRoles.ensure(storeId, request.roleCode());
        AccountRef inviter = accountLookup.requireByEmail(email.trim());
        StaffInvitations.Issued issued = staffInvitations.create(storeId, inviter.accountId(), normalizedEmail, roleId);
        mailer.send(issued);
        return StaffInvitationResponse.from(issued.invitation());
    }
}
