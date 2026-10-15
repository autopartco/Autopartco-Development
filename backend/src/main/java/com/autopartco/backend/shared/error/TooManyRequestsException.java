package com.autopartco.backend.shared.error;

import lombok.Getter;

/** 429 Too Many Requests: se pidió una acción antes del enfriamiento. Lo mapea {@link GlobalExceptionHandler}. */
@Getter
public class TooManyRequestsException extends RuntimeException {
    private final String code;
    /** Datos adicionales seguros para el cliente; se serializan en {@code error.details}. */
    private final transient Object details;

    public TooManyRequestsException(String code, String message) {
        this(code, message, null);
    }

    public TooManyRequestsException(String code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }
}
