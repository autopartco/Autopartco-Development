package com.autopartco.backend.core.questions;

import java.time.OffsetDateTime;

/** Respuesta del almacén a una pregunta. */
public record QuestionAnswerView(String text, OffsetDateTime answeredAt) {
}
