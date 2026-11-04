package com.autopartco.backend.core.catalog;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Alta (name obligatorio, lo valida el servicio) o edición parcial de un fabricante por operaciones. */
public record ManufacturerRequest(
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres.")
        @Pattern(regexp = "(?s).*\\S.*", message = "El nombre no puede estar vacío.")
        String name,
        /** Solo {@code true} (aprobar un fabricante sugerido). */
        Boolean approved
) {
    @AssertTrue(message = "Debe proporcionar un nombre o approved para actualizar.")
    public boolean hasChanges() {
        return name != null || approved != null;
    }
}
