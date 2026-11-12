package com.autopartco.backend.marketplace.questions;

/**
 * Pregunta nueva. La longitud (10 a 300 caracteres tras recortar) y la ausencia de datos de contacto las valida el
 * servicio para responder con {@code QUESTION_INVALID} y {@code QUESTION_CONTACT_NOT_ALLOWED}.
 */
public record ProductQuestionRequest(String question) {
}
