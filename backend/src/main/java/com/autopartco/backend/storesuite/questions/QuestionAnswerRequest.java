package com.autopartco.backend.storesuite.questions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Respuesta del almacén: hasta 1000 caracteres y sin datos de contacto (esto último lo valida el servicio). */
public record QuestionAnswerRequest(
        @NotBlank(message = "Escribe la respuesta.")
        @Size(max = 1000, message = "La respuesta no puede superar 1000 caracteres.")
        String text
) {
}
