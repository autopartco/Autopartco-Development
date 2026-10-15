package com.autopartco.backend.core.identity.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PersonAddressRequest(
        @NotBlank(message = "La etiqueta es obligatoria.")
        @Size(max = 100, message = "La etiqueta no puede superar 100 caracteres.")
        String label,

        @NotNull(message = "La ubicación es obligatoria.")
        @Valid
        AddressLocationRequest location,

        boolean isDefault
) {
}
