package com.autopartco.backend.core.identity.internal;

/** Pide enviar un aviso de seguridad (contraseña o correo cambiados) cuando la transacción se confirme. */
record SecurityNoticeRequested(String email, String fullName, String title, String message) {
}
