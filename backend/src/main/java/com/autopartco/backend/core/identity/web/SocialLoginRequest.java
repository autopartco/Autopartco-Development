package com.autopartco.backend.core.identity.web;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /auth/social/{provider}}. {@code token}: ID token de Google o access token de Facebook.
 * {@code registration} solo se envía tras un 409 {@code SOCIAL_REGISTRATION_REQUIRED} (cuenta nueva).
 */
public record SocialLoginRequest(
        @NotBlank(message = "El token del proveedor es obligatorio.")
        @Size(max = 8192, message = "El token del proveedor no es válido.")
        String token,
        @Valid
        Registration registration
) {
    /** Datos que el proveedor no entrega y la cuenta exige. Nombre y apellido opcionales (sobrescriben los del proveedor). */
    public record Registration(
            @Size(max = 100, message = "El nombre no puede superar 100 caracteres.")
            String name,
            @Size(max = 100, message = "El apellido no puede superar 100 caracteres.")
            String surname,
            @NotBlank(message = "La identificación es obligatoria.")
            @Size(max = 20, message = "La identificación no puede superar 20 caracteres.")
            String identification,
            @NotNull(message = "La fecha de nacimiento es obligatoria.")
            @Past(message = "La fecha de nacimiento debe estar en el pasado.")
            LocalDate dateBirth,
            @NotBlank(message = "El teléfono es obligatorio.")
            @Size(max = 20, message = "El teléfono no puede superar 20 caracteres.")
            String numberPhone
    ) {
    }
}
