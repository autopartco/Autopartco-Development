package com.autopartco.backend.core.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Confirma con el código enviado la acción pedida ({@code ENABLE} por defecto, o {@code DISABLE}). */
public record TwoFactorConfirmRequest(
        @NotBlank(message = "El código es obligatorio.")
        @Size(max = 12, message = "El código no es válido.")
        String code,

        @Pattern(regexp = "ENABLE|DISABLE", message = "La acción debe ser ENABLE o DISABLE.")
        String action
) {
    public String actionOrDefault() {
        return action == null ? "ENABLE" : action;
    }
}
