package com.autopartco.backend.core.identity.web;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TwoFactorLoginRequest(
        @NotNull(message = "El desafío es obligatorio.")
        UUID challengeId,

        @NotBlank(message = "El código es obligatorio.")
        @Size(max = 12, message = "El código no es válido.")
        String code
) {
}
