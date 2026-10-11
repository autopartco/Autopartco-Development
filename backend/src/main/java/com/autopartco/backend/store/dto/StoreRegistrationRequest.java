package com.autopartco.backend.store.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StoreRegistrationRequest(
        @NotBlank(message = "El nombre del almacén es obligatorio.")
        @Size(max = 255, message = "El nombre del almacén no puede superar 255 caracteres.")
        String businessName,

        @NotBlank(message = "El NIT es obligatorio.")
        @Size(max = 20, message = "El NIT no puede superar 20 caracteres.")
        String nit,

        @NotBlank(message = "El correo del almacén es obligatorio.")
        @Email(message = "El correo del almacén debe tener un formato válido.")
        @Size(max = 200, message = "El correo no puede superar 200 caracteres.")
        String businessEmail
) {
}