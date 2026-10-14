package com.autopartco.backend.shared.error;

import lombok.Getter;

/** Pago rechazado o requerido (HTTP 402). */
@Getter
public class PaymentRequiredException extends RuntimeException {
    private final String code;
    private final transient Object details;

    public PaymentRequiredException(String code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }
}
