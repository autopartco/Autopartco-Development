package com.autopartco.backend.storesuite.reviews;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Respuesta del almacén a una reseña: hasta 1000 caracteres y sin enlaces (esto último lo valida el servicio). */
public record ReviewReplyRequest(
        @NotBlank(message = "Escribe la respuesta.")
        @Size(max = 1000, message = "La respuesta no puede superar 1000 caracteres.")
        String text
) {
}
