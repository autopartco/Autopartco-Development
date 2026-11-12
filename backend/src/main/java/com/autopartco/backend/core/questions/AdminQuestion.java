package com.autopartco.backend.core.questions;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Pregunta vista por administración. {@code author} es nombre + inicial del apellido (nunca correo).
 * {@code hiddenAt} y {@code hiddenReason} solo si está oculta.
 */
public record AdminQuestion(
        UUID id,
        String question,
        String author,
        OffsetDateTime createdAt,
        QuestionAnswerView answer,
        UUID storeId,
        String storeName,
        UUID productId,
        String productName,
        boolean hidden,
        OffsetDateTime hiddenAt,
        String hiddenReason
) {
}
