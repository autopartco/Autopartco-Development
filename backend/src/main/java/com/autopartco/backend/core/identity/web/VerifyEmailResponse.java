package com.autopartco.backend.core.identity.web;

import java.util.UUID;

public record VerifyEmailResponse(
        UUID accountId,
        String email,
        String state,
        String message
) {
}
