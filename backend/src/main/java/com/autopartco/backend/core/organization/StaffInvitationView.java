package com.autopartco.backend.core.organization;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Invitación a colaborador vista desde fuera de {@code organization}. Nunca incluye el token ni su hash. */
public record StaffInvitationView(
        UUID id,
        UUID storeId,
        String storeName,
        String email,
        UUID roleId,
        String roleName,
        List<String> permissionCodes,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
}
