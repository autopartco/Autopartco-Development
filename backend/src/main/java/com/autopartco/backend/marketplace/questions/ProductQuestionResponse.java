package com.autopartco.backend.marketplace.questions;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.autopartco.backend.marketplace.reviews.StoreReviewService;

/**
 * Pregunta pública de un producto. {@code author} es nombre + inicial del apellido («Ana P.»), nunca el apellido
 * completo ni el correo. {@code answer}: respuesta del almacén o {@code null} (pendiente, solo la ve su autor).
 * {@code mine}: la hizo quien consulta.
 */
public record ProductQuestionResponse(
        UUID id,
        String question,
        String author,
        OffsetDateTime createdAt,
        Answer answer,
        boolean mine
) {
    public record Answer(String text, OffsetDateTime answeredAt) {
    }

    static ProductQuestionResponse of(QuestionQueries.QuestionRow row, UUID viewerPersonId) {
        return new ProductQuestionResponse(row.id(), row.question(),
                StoreReviewService.authorName(row.name(), row.surname()), row.createdAt(),
                row.answer() == null ? null : new Answer(row.answer(), row.answeredAt()),
                viewerPersonId != null && viewerPersonId.equals(row.personId()));
    }
}
