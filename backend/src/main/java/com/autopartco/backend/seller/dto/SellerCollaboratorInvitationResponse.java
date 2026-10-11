package com.autopartco.backend.seller.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SellerCollaboratorInvitationResponse(
        UUID id,
        String email,
        String roleName,
        List<String> permissionCodes,
        LocalDateTime expiresAt,
        boolean expired,
        LocalDateTime createdAt
) {
}
