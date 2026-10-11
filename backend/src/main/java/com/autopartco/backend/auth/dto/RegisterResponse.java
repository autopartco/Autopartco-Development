package com.autopartco.backend.auth.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RegisterResponse(
        UUID accountId,
        UUID personId,
        String email,
        String accountType,
        String state,
        boolean verificationEmailSent,
        LocalDateTime verificationTokenExpiresAt
) {
}
