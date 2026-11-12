package com.autopartco.backend.operations.questions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo de la moderación (3 a 200 caracteres); queda en la pregunta y en la auditoría. */
public record HideQuestionRequest(
        @NotBlank(message = "Indica el motivo.")
        @Size(min = 3, max = 200, message = "El motivo debe tener entre 3 y 200 caracteres.")
        String reason
) {
}
