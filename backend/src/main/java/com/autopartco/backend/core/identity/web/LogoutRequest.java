package com.autopartco.backend.core.identity.web;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "El refresh token es obligatorio.")
        String refreshToken
) {
}
