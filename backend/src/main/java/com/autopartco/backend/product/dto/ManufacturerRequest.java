package com.autopartco.backend.product.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ManufacturerRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres.")
        @Pattern(regexp = "(?s).*\\S.*", message = "El nombre no puede estar vacío.")
        String name
) {
    @AssertTrue(message = "Debe proporcionar un nombre para actualizar.")
    public boolean hasName() {
        return name != null;
    }
}