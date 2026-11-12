package com.autopartco.backend.marketplace.questions;

import java.util.UUID;

/** Se publicó una pregunta nueva sobre un producto. Se procesa después del commit y en segundo plano. */
public record ProductQuestionAsked(UUID questionId) {
}
