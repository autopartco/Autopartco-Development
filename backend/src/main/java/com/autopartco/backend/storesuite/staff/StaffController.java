package com.autopartco.backend.storesuite.staff;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.organization.PermissionOption;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** Equipo del almacén: colaboradores, permisos asignables e invitaciones (con permiso de almacén). */
@RestController
@RequestMapping(ApiPaths.STORES + "/{storeId}/staff")
@AllArgsConstructor
public class StaffController {
    private final ListStaff listStaff;
    private final ListAssignablePermissions listAssignablePermissions;
    private final InviteStaffMember inviteStaffMember;
    private final ResendStaffInvitation resendStaffInvitation;
    private final RevokeStaffInvitation revokeStaffInvitation;
    private final AuditService auditService;
    private final AccountLookup accountLookup;

    @GetMapping
    public StaffResponse list(@PathVariable UUID storeId, Authentication authentication) {
        return listStaff.execute(storeId, authentication.getName());
    }

    @GetMapping("/permission-options")
    public List<PermissionOption> permissionOptions(@PathVariable UUID storeId, Authentication authentication) {
        return listAssignablePermissions.execute(storeId, authentication.getName());
    }

    @PostMapping("/invitations")
    public StaffInvitationResponse invite(
            @PathVariable UUID storeId,
            @Valid @RequestBody StaffInviteRequest request,
            Authentication authentication
    ) {
        StaffInvitationResponse invitation = inviteStaffMember.execute(storeId, authentication.getName(), request);
        audit(authentication, "STAFF_INVITED", invitation.id(), Map.of(
                "storeId", storeId.toString(),
                "invitedEmail", invitation.email(),
                "role", invitation.roleName() == null ? "" : invitation.roleName()));
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
    public void revoke(
            @PathVariable UUID storeId,
            @PathVariable UUID invitationId,
            Authentication authentication
    ) {
        revokeStaffInvitation.execute(storeId, invitationId, authentication.getName());
        audit(authentication, "STAFF_INVITATION_REVOKED", invitationId, Map.of("storeId", storeId.toString()));
    }

    private void audit(Authentication authentication, String action, UUID invitationId, Map<String, Object> metadata) {
        UUID actorId = accountLookup.findByEmail(authentication.getName()).map(AccountRef::accountId).orElse(null);
        auditService.record(actorId, action, "STAFF_INVITATION", invitationId, metadata);
    }
}
