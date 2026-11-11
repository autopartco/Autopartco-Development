package com.autopartco.backend.core.identity.web;

/** Respuesta al pedir un código de activación/desactivación. {@code sent=false} si hubo que esperar. */
public record TwoFactorSetupResponse(boolean sent, long retryAfterSeconds, long codeValidMinutes) {
}
