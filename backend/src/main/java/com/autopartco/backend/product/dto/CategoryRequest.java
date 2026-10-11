package com.autopartco.backend.product.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "El código es obligatorio.")
        @Size(max = 100, message = "El código no puede superar 100 caracteres.")
        @Pattern(regexp = "[A-Za-z0-9_-]+", message = "El código solo puede contener letras, números, guion y guion bajo.")
        String code
) {
    @AssertTrue(message = "Debe proporcionar un código para actualizar.")
    public boolean hasCode() {
        return code != null;
    }
}