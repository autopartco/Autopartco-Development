package com.autopartco.backend.shared.error;

import lombok.Getter;

@Getter
public class NotFoundException extends RuntimeException {
    private final String code;
    private final transient Object details;

    public NotFoundException(String code, String message) {
        this(code, message, null);
    }

    public NotFoundException(String code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }
}
