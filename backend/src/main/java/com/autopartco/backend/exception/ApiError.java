package com.autopartco.backend.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiError {
    public String code;
    public String message;
    public Object details;
}
