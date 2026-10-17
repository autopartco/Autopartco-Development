package com.autopartco.backend.core.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sugerencia de fabricante de un almacén. {@code confirmNew=true} crea aunque existan similares. El nombre se
 * normaliza (sin espacios en los extremos y con espacios internos colapsados) y no admite caracteres de control.
 */
public record StoreManufacturerRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres.")
        @Pattern(regexp = "^[^\\p{Cc}]*$", message = "El nombre contiene caracteres no permitidos.")
        String name,
        Boolean confirmNew
) {
    public StoreManufacturerRequest {
        name = normalizeName(name);
    }

    /** Sin espacios en los extremos y con espacios internos colapsados; {@code null} se conserva. */
    public static String normalizeName(String name) {
        return name == null ? null : name.strip().replaceAll("\\p{Zs}{2,}", " ");
    }
}
