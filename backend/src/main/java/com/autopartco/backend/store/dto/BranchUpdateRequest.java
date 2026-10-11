package com.autopartco.backend.store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BranchUpdateRequest(
        @Size(max = 255)
        @Pattern(regexp = "(?s).*\\S.*", message = "El nombre no puede estar vacío.")
        String name,

        @Size(max = 10)
        @Pattern(regexp = "[0-9]{7,10}", message = "El teléfono debe tener entre 7 y 10 dígitos.")
        String numberPhone,

        @Valid
        BranchLocationRequest location
) {
    @AssertTrue(message = "Debe proporcionar al menos un dato para actualizar.")
    public boolean hasChanges() {
        return name != null || numberPhone != null || location != null;
    }
}