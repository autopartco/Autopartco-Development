package com.autopartco.backend.core.questions;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Pregunta vista desde el portal del almacén. {@code author} es nombre + inicial del apellido (nunca correo).
 * {@code hidden}: la ocultó la plataforma (no se muestra al público); el motivo no se expone. {@code answer} es
 * {@code null} si sigue pendiente.
 */
public record PortalQuestion(
        UUID id,
        String question,
        String author,
        OffsetDateTime createdAt,
        QuestionAnswerView answer,
        boolean hidden,
        UUID productId,
        String productName
) {
}
