package com.autopartco.backend.shared.error;

import lombok.Getter;

@Getter
public class UnauthorizedException extends RuntimeException {
    private final String code;

    public UnauthorizedException(String code, String message) {
        super(message);
        this.code = code;
    }
}