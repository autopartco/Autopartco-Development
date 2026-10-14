package com.autopartco.backend.core.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @NotBlank(message = "El refresh token es obligatorio.")
        @Size(max = 200, message = "El refresh token no es válido.")
        String refreshToken
) {
}
