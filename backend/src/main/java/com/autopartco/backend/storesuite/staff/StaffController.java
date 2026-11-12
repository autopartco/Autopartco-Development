package com.autopartco.backend.storesuite.staff;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** Equipo del almacén: colaboradores, roles predefinidos e invitaciones (con permiso de almacén). */
@RestController
@RequestMapping(ApiPaths.STORES + "/{storeId}/staff")
@AllArgsConstructor
public class StaffController {
    private final ListStaff listStaff;
    private final ListStaffRoles listStaffRoles;
    private final ManageStaffMember manageStaffMember;
    private final InviteStaffMember inviteStaffMember;
    private final ResendStaffInvitation resendStaffInvitation;
    private final RevokeStaffInvitation revokeStaffInvitation;
    private final AuditService auditService;
    private final AccountLookup accountLookup;

    @GetMapping
    public StaffResponse list(@PathVariable UUID storeId, Authentication authentication) {
        return listStaff.execute(storeId, authentication.getName());
    }

    @GetMapping("/roles")
    public List<StaffRoleResponse> roles(@PathVariable UUID storeId, Authentication authentication) {
        return listStaffRoles.execute(storeId, authentication.getName());
    }

    @PatchMapping("/members/{memberId}/role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeRole(
            @PathVariable UUID storeId,
            @PathVariable UUID memberId,
            @Valid @RequestBody StaffRoleRequest request,
            Authentication authentication
    ) {
        manageStaffMember.changeRole(storeId, memberId, authentication.getName(), request.roleCode());
        audit(authentication, "STAFF_ROLE_CHANGED", "COLLABORATOR", memberId, Map.of(
                "storeId", storeId.toString(),
                "role", request.roleCode()));
    }

    @PatchMapping("/members/{memberId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID storeId, @PathVariable UUID memberId, Authentication authentication) {
        if (manageStaffMember.setActive(storeId, memberId, authentication.getName(), false)) {
            audit(authentication, "STAFF_DEACTIVATED", "COLLABORATOR", memberId, Map.of("storeId", storeId.toString()));
        }
    }

    @PatchMapping("/members/{memberId}/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reactivate(@PathVariable UUID storeId, @PathVariable UUID memberId, Authentication authentication) {
        if (manageStaffMember.setActive(storeId, memberId, authentication.getName(), true)) {
            audit(authentication, "STAFF_REACTIVATED", "COLLABORATOR", memberId, Map.of("storeId", storeId.toString()));
        }
    }

    @PostMapping("/invitations")
    public StaffInvitationResponse invite(
            @PathVariable UUID storeId,
            @Valid @RequestBody StaffInviteRequest request,
            Authentication authentication
    ) {
        StaffInvitationResponse invitation = inviteStaffMember.execute(storeId, authentication.getName(), request);
        audit(authentication, "STAFF_INVITED", "STAFF_INVITATION", invitation.id(), Map.of(
                "storeId", storeId.toString(),
                "invitedEmail", invitation.email(),
                "role", invitation.roleCode() == null ? "" : invitation.roleCode()));
        return invitation;
    }

    @PostMapping("/invitations/{invitationId}/resend")
    public StaffInvitationResponse resend(
            @PathVariable UUID storeId,
            @PathVariable UUID invitationId,
            Authentication authentication
    ) {
        return resendStaffInvitation.execute(storeId, invitationId, authentication.getName());
    }

    @PatchMapping("/invitations/{invitationId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @PathVariable UUID storeId,
            @PathVariable UUID invitationId,
            Authentication authentication
    ) {
        revokeStaffInvitation.execute(storeId, invitationId, authentication.getName());
        audit(authentication, "STAFF_INVITATION_REVOKED", "STAFF_INVITATION", invitationId, Map.of("storeId", storeId.toString()));
    }

    private void audit(
            Authentication authentication,
            String action,
            String entity,
            UUID entityId,
            Map<String, Object> metadata
    ) {
        UUID actorId = accountLookup.findByEmail(authentication.getName()).map(AccountRef::accountId).orElse(null);
        auditService.record(actorId, action, entity, entityId, metadata);
    }
}
