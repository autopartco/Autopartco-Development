package com.autopartco.backend.shared.error;

import lombok.Getter;

@Getter
public class ConflictException extends RuntimeException {
    private final String code;
    
    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }
}
