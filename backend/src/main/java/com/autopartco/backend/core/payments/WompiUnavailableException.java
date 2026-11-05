package com.autopartco.backend.core.payments;

/**
 * La API de Wompi no respondió, respondió con un error distinto de 404 o la llave privada no está configurada. El
 * llamador decide: el {@code sync} responde con el estado actual y la conciliación lo reintenta en la siguiente
 * ronda.
 */
public class WompiUnavailableException extends RuntimeException {
    public WompiUnavailableException(String message) {
        super(message);
    }

    public WompiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
