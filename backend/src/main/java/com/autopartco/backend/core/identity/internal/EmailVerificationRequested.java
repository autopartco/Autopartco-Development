package com.autopartco.backend.core.identity.internal;

/**
 * Pide enviar el correo de verificación (enlace y código) cuando la transacción que lo publicó se confirme.
 * Lleva secretos: no debe registrarse ni serializarse.
 */
record EmailVerificationRequested(
        String email,
        String fullName,
        String verificationLink,
        String code,
        long linkValidMinutes,
        long codeValidMinutes
) {
    @Override
    public String toString() {
        return "EmailVerificationRequested[redacted]";
    }
}
