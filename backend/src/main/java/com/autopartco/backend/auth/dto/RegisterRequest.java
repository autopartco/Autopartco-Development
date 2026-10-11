package com.autopartco.backend.auth.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres.")
        String name,

        @NotBlank(message = "El apellido es obligatorio.")
        @Size(max = 100, message = "El apellido no puede superar 100 caracteres.")
        String surname,

        @NotBlank(message = "La identificación es obligatoria.")
        @Size(max = 20, message = "La identificación no puede superar 20 caracteres.")
        String identification,

        @NotNull(message = "La fecha de nacimiento es obligatoria.")
        @Past(message = "La fecha de nacimiento debe estar en el pasado.")
        LocalDate dateBirth,

        @NotBlank(message = "El teléfono es obligatorio.")
        @Size(max = 10, message = "El teléfono no puede superar 10 caracteres.")
        String numberPhone,

        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo debe tener un formato válido.")
        @Size(max = 100, message = "El correo no puede superar 100 caracteres.")
        String email,

        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "La contraseña debe incluir mayúscula, minúscula, número y carácter especial."
        )
        String password
) {
}
