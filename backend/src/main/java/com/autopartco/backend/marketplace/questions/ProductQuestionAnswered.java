package com.autopartco.backend.marketplace.questions;

import java.util.UUID;

/**
 * El almacén respondió por primera vez una pregunta (editar la respuesta no lo repite). Se procesa después del commit
 * y en segundo plano.
 */
public record ProductQuestionAnswered(UUID questionId) {
}
