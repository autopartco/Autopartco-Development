package com.autopartco.backend.core.identity.web;

/** Estado de la 2FA por correo tras activarla o desactivarla ({@code method} siempre {@code EMAIL}). */
public record TwoFactorStatusResponse(boolean enabled, String method) {
}
