package com.autopartco.backend.seller.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SellerCollaboratorResponse(
        UUID storeId,
        List<Member> members,
        List<SellerCollaboratorInvitationResponse> invitations
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
