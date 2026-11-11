package com.autopartco.backend.core.organization;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cambios parciales de una sucursal ({@code null} no cambia). {@code whatsapp} es E.164 de Colombia y la cadena vacía lo
 * quita. {@code openingHours} sigue {@link OpeningHours}: el objeto vacío {@code {}} quita el horario.
 */
public record BranchUpdateRequest(
        @Size(max = 255)
        @Pattern(regexp = "(?s).*\\S.*", message = "El nombre no puede estar vacío.")
        String name,

        @Size(max = 10)
        @Pattern(regexp = "[0-9]{7,10}", message = "El teléfono debe tener entre 7 y 10 dígitos.")
        String numberPhone,

        @Valid
        BranchLocationRequest location,

        @Pattern(regexp = "^$|^\\+57\\d{10}$", message = "El WhatsApp debe tener el formato +57 y 10 dígitos.")
        String whatsapp,

        Map<String, Object> openingHours
) {
    @AssertTrue(message = "Debe proporcionar al menos un dato para actualizar.")
    public boolean hasChanges() {
        return name != null || numberPhone != null || location != null || whatsapp != null || openingHours != null;
    }
}
