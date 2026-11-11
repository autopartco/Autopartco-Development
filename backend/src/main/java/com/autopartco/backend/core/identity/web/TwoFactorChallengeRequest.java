package com.autopartco.backend.core.identity.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record TwoFactorChallengeRequest(
        @NotNull(message = "El desafío es obligatorio.")
        UUID challengeId
) {
}
