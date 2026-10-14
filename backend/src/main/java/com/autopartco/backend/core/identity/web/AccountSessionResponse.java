package com.autopartco.backend.core.identity.web;

import java.time.LocalDateTime;
import java.util.UUID;

public record AccountSessionResponse(
        UUID sessionId,
        String ipAddress,
        String userAgent,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        boolean current
) {
}
