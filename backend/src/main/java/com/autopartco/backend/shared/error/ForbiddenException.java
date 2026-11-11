package com.autopartco.backend.shared.error;

import lombok.Getter;

/** 403 Forbidden con código propio y datos opcionales. Lo mapea {@link GlobalExceptionHandler}. */
@Getter
public class ForbiddenException extends RuntimeException {
    private final String code;
    /** Datos adicionales seguros para el cliente; se serializan en {@code error.details}. */
    private final transient Object details;

    public ForbiddenException(String code, String message) {
        this(code, message, null);
    }

    public ForbiddenException(String code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }
}
