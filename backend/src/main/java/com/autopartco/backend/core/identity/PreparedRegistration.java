package com.autopartco.backend.core.identity;

/**
 * Registro de cuenta ya persistido cuyo correo de verificación aún no se ha enviado. Permite a quien orquesta
 * (registro de vendedor) enviar el correo solo cuando el resto de su transacción ha ido bien.
 */
public record PreparedRegistration(
        RegisterResponse response,
        String fullName,
        String verificationLink
) {
}
