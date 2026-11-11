package com.autopartco.backend.core.identity.web;

import jakarta.validation.constraints.Pattern;

/** Pide un código para activar ({@code ENABLE}, por defecto) o desactivar ({@code DISABLE}) la 2FA por correo. */
public record TwoFactorSetupRequest(
        @Pattern(regexp = "ENABLE|DISABLE", message = "La acción debe ser ENABLE o DISABLE.")
        String action
) {
    public String actionOrDefault() {
        return action == null ? "ENABLE" : action;
    }
}
