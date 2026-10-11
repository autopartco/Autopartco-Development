package com.autopartco.backend.store.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StoreUpdateRequest(
        @Size(max = 255, message = "El nombre del almacén no puede superar 255 caracteres.")
        @Pattern(regexp = "(?s).*\\S.*", message = "El nombre del almacén no puede estar vacío.")
        String businessName,

        @Size(max = 20, message = "El NIT no puede superar 20 caracteres.")
        @Pattern(regexp = "(?s).*\\S.*", message = "El NIT no puede estar vacío.")
        String nit,

        @Email(message = "El correo del almacén debe tener un formato válido.")
        @Size(max = 200, message = "El correo no puede superar 200 caracteres.")
        @Pattern(regexp = "(?s).*\\S.*", message = "El correo no puede estar vacío.")
        String businessEmail
) {
    @AssertTrue(message = "Debe proporcionar al menos un dato permitido para actualizar.")
    public boolean hasChanges() {
        return businessName != null || nit != null || businessEmail != null;
    }
}