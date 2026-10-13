package com.autopartco.backend.storesuite.staff;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.autopartco.backend.core.organization.StaffInvitationView;

public record StaffInvitationResponse(
        UUID id,
        String email,
        String roleName,
        List<String> permissionCodes,
        LocalDateTime expiresAt,
        boolean expired,
        LocalDateTime createdAt
) {
    static StaffInvitationResponse from(StaffInvitationView invitation) {
        return new StaffInvitationResponse(
                invitation.id(),
                invitation.email(),
                invitation.roleName(),
                invitation.permissionCodes(),
                invitation.expiresAt(),
                !invitation.expiresAt().isAfter(LocalDateTime.now()),
                invitation.createdAt()
        );
    }
}
