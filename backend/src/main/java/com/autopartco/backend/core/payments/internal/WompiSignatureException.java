package com.autopartco.backend.core.payments.internal;

/** Firma de evento ausente, incompleta o inválida: el webhook responde 401 y no guarda nada. */
public class WompiSignatureException extends RuntimeException {
    public WompiSignatureException(String message) {
        super(message);
    }
}
