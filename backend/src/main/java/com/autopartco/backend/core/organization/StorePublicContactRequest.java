package com.autopartco.backend.core.organization;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Contacto público del almacén. En cada campo: {@code null} no cambia y cadena vacía lo borra. Las redes se guardan
 * como usuario (sin URL) para construir el enlace siempre hacia la red correcta.
 */
public record StorePublicContactRequest(
        @Pattern(regexp = "^$|^\\+57\\d{10}$", message = "El WhatsApp debe tener el formato +57 y 10 dígitos.")
        String whatsapp,
        @Pattern(regexp = "^$|^\\+57\\d{10}$", message = "El teléfono debe tener el formato +57 y 10 dígitos.")
        String phone,
        @Size(max = 200, message = "El sitio web no puede superar 200 caracteres.")
        @Pattern(regexp = "^$|^https://[^\\s/$.?#][^\\s]*$", message = "El sitio web debe empezar por https://.")
        String website,
        @Pattern(regexp = "^$|^[A-Za-z0-9._]{1,30}$", message = "Escribe solo el usuario de Instagram, sin @ ni enlace.")
        String instagram,
        @Pattern(regexp = "^$|^[A-Za-z0-9.]{1,50}$", message = "Escribe solo el usuario de Facebook, sin enlace.")
        String facebook,
        @Pattern(regexp = "^$|^[A-Za-z0-9._]{1,30}$", message = "Escribe solo el usuario de TikTok, sin @ ni enlace.")
        String tiktok
) {
    boolean hasChanges() {
        return whatsapp != null || phone != null || website != null || instagram != null || facebook != null
                || tiktok != null;
    }
}
