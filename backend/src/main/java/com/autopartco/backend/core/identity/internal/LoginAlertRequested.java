package com.autopartco.backend.core.identity.internal;

/** Pide avisar por correo de un inicio de sesión desde un dispositivo nuevo, tras confirmar la transacción. */
record LoginAlertRequested(String email, String fullName, String when, String device, String maskedIp) {
    @Override
    public String toString() {
        return "LoginAlertRequested[redacted]";
    }
}
