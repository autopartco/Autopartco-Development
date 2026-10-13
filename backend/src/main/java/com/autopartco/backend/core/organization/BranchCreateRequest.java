package com.autopartco.backend.core.organization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BranchCreateRequest(
        @NotBlank(message = "El nombre de la sucursal es obligatorio.")
        @Size(max = 255)
        String name,

        @NotBlank(message = "El teléfono es obligatorio.")
        @Size(max = 10)
        @Pattern(regexp = "[0-9]{7,10}", message = "El teléfono debe tener entre 7 y 10 dígitos.")
        String numberPhone,

        @NotNull(message = "La ubicación es obligatoria.")
        @Valid
        BranchLocationRequest location
) {
}