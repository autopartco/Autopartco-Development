package com.autopartco.backend.storesuite.staff;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.autopartco.backend.core.organization.StaffMember;

public record StaffResponse(
        UUID storeId,
        List<Member> members,
        List<StaffInvitationResponse> invitations
) {
    public record Member(
            UUID id,
            String name,
            String surname,
            String email,
            String status,
            List<String> roleNames,
            List<String> permissionCodes
    ) {
        static Member from(StaffMember member) {
            return new Member(
                    member.id(),
                    member.name(),
                    member.surname(),
                    member.email(),
                    member.status(),
                    member.roleNames(),
                    member.permissionCodes()
            );
        }
    }

    public record Accepted(
            UUID storeId,
            String storeName,
            String email,
            String roleName
    ) {
    }

    public record InvitationPreview(
            UUID storeId,
            String storeName,
            String email,
            String roleName,
            List<String> permissionCodes,
            boolean accountCreationRequired,
            LocalDateTime expiresAt
    ) {
    }
}
