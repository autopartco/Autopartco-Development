package com.autopartco.backend.core.organization;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BranchLocationRequest(
        @NotBlank(message = "La dirección es obligatoria.")
        @Size(max = 255)
        String addressLine,

        @Size(max = 255)
        String addressComplement,

        @NotBlank(message = "La ciudad es obligatoria.")
        @Size(max = 150)
        String city,

        @NotBlank(message = "El departamento es obligatorio.")
        @Size(max = 150)
        String department,

        @Size(max = 100)
        String country,

        @Size(max = 20)
        String postalCode,

        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        BigDecimal latitude,

        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        BigDecimal longitude
) {
}