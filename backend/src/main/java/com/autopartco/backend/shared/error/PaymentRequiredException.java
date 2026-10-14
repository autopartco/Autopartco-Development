package com.autopartco.backend.shared.error;

import lombok.Getter;

/** 402 Payment Required: el pago no se aprobó. Lo mapea {@link GlobalExceptionHandler}. */
@Getter
public class PaymentRequiredException extends RuntimeException {
    private final String code;
    /** Datos adicionales seguros para el cliente; se serializan en {@code error.details}. */
    private final Object details;

    public PaymentRequiredException(String code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }
}
