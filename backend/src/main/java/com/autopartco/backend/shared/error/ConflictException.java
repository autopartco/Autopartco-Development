package com.autopartco.backend.shared.error;

import lombok.Getter;

@Getter
public class ConflictException extends RuntimeException {
    private final String code;
    private final transient Object details;

    public ConflictException(String code, String message) {
        this(code, message, null);
    }

    public ConflictException(String code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }
}
