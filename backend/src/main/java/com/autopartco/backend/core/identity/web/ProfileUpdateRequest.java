package com.autopartco.backend.core.identity.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres.")
        @Pattern(regexp = "(?s).*\\S.*", message = "El nombre no puede estar vacío.")
        String name,

        @Size(max = 100, message = "El apellido no puede superar 100 caracteres.")
        @Pattern(regexp = "(?s).*\\S.*", message = "El apellido no puede estar vacío.")
        String surname,

        @Size(max = 20, message = "La identificación no puede superar 20 caracteres.")
        @Pattern(regexp = "(?s).*\\S.*", message = "La identificación no puede estar vacía.")
        String identification,

        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "La fecha debe tener el formato yyyy-MM-dd.")
        String dateBirth,

        @Size(max = 10, message = "El teléfono no puede superar 10 caracteres.")
        @Pattern(regexp = "(?s).*\\S.*", message = "El teléfono no puede estar vacío.")
        String numberPhone
) {
    @AssertTrue(message = "Debe proporcionar al menos un dato permitido para actualizar.")
    public boolean hasChanges() {
        return name != null
                || surname != null
                || identification != null
                || dateBirth != null
                || numberPhone != null;
    }

    @AssertTrue(message = "La fecha de nacimiento debe ser válida y estar en el pasado.")
    public boolean hasValidDateBirth() {
        if (dateBirth == null) {
            return true;
        }

        try {
            return LocalDate.parse(dateBirth).isBefore(LocalDate.now());
        } catch (DateTimeParseException exception) {
            return false;
        }
    }
}