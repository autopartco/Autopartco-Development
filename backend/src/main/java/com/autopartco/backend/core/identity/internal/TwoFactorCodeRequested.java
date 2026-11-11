package com.autopartco.backend.core.identity.internal;

/**
 * Pide enviar un código de verificación en dos pasos cuando la transacción que lo publicó se confirme.
 * Lleva el código: no debe registrarse ni serializarse.
 */
record TwoFactorCodeRequested(String email, String fullName, String code, String purposeText, long validMinutes) {
    @Override
    public String toString() {
        return "TwoFactorCodeRequested[redacted]";
    }
}
