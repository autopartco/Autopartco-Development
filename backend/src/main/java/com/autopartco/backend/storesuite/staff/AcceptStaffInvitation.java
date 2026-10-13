package com.autopartco.backend.storesuite.staff;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountProvisioning;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.identity.InvitedAccountDetails;
import com.autopartco.backend.core.organization.StaffDirectory;
import com.autopartco.backend.core.organization.StaffInvitationView;
import com.autopartco.backend.core.organization.StaffInvitations;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;

import lombok.AllArgsConstructor;

/**
 * Aceptación pública de una invitación con su token de un solo uso. En una sola transacción: bloquea almacén e
 * invitación, comprueba el cupo, usa la cuenta existente (bloqueada) o crea una nueva con los datos enviados,
 * da de alta al colaborador y marca la invitación como aceptada.
 */
@Service
@AllArgsConstructor
public class AcceptStaffInvitation {
    private static final String ACTIVE_STATE = "ACTIVE";
    private static final String LOCKED_STATE = "LOCKED";

    private final StaffInvitations staffInvitations;
    private final StaffSeats staffSeats;
    private final AccountLookup accountLookup;
    private final AccountProvisioning accountProvisioning;
    private final StaffDirectory staffDirectory;

    @Transactional
    public StaffResponse.Accepted execute(InvitationAcceptRequest request) {
        StaffInvitationView invitation = staffInvitations.lockPendingByToken(request.token());
        staffSeats.reserve(invitation.storeId(), 0);

        AccountRef account = accountLookup.lockByEmail(invitation.email())
                .orElseGet(() -> accountProvisioning.createInvitedAccount(
                        invitation.email(),
                        requireDetails(request.account())
                ));
        if (LOCKED_STATE.equals(account.stateCode())) {
            throw new ConflictException(
                    "SELLER_COLLABORATOR_ACCOUNT_LOCKED",
                    "La cuenta está bloqueada y no puede aceptar invitaciones."
            );
        }
        if (!ACTIVE_STATE.equals(account.stateCode())) {
            accountProvisioning.activate(account.accountId());
        }
        staffDirectory.addCollaborator(invitation.storeId(), account.personId(), invitation.roleId());

        staffInvitations.markAccepted(invitation.id());
        return new StaffResponse.Accepted(
                invitation.storeId(),
                invitation.storeName(),
                account.email(),
                invitation.roleName()
        );
    }

    private InvitedAccountDetails requireDetails(InvitationAcceptRequest.AccountDetails details) {
        if (details == null) {
            throw new BadRequestException(
                    "SELLER_COLLABORATOR_ACCOUNT_REQUIRED",
                    "Completa tus datos personales y crea una contraseña para aceptar la invitación."
            );
        }
        return new InvitedAccountDetails(
                details.name(),
                details.surname(),
                details.identification(),
                details.dateBirth(),
                details.numberPhone(),
                details.password()
        );
    }
}
