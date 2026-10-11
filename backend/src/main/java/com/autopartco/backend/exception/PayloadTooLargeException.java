package com.autopartco.backend.exception;

import lombok.Getter;

@Getter
public class PayloadTooLargeException extends RuntimeException {
    private final String code;

    public PayloadTooLargeException(String code, String message) {
        super(message);
        this.code = code;
    }
}
