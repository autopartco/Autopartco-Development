package com.autopartco.backend.shared.error;

import lombok.Getter;

@Getter
public class BadRequestException extends RuntimeException {
    private final String code;
    /** Datos adicionales seguros para el cliente; se serializan en {@code error.details}. */
    private final Object details;

    public BadRequestException(String code, String message) {
        this(code, message, null);
    }

    public BadRequestException(String code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }
}
