package com.autopartco.backend.core.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TwoFactorDisableRequest(
        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(max = 72, message = "La contraseña no puede superar 72 caracteres.")
        String password
) {
}
