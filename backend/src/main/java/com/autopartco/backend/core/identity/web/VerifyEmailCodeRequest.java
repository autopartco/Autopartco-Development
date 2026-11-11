package com.autopartco.backend.core.identity.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailCodeRequest(
        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo debe tener un formato válido.")
        @Size(max = 100, message = "El correo no puede superar 100 caracteres.")
        String email,

        @NotBlank(message = "El código es obligatorio.")
        @Size(max = 12, message = "El código no es válido.")
        String code
) {
}
