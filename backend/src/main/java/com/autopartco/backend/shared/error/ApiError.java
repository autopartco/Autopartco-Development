package com.autopartco.backend.shared.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiError {
    public String code;
    public String message;
    public Object details;
}
