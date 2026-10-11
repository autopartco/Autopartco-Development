package com.autopartco.backend.auth.dto;

import java.util.UUID;

public record VerifyEmailResponse(
        UUID accountId,
        String email,
        String state,
        String message
) {
}
