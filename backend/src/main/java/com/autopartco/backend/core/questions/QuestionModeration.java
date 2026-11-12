package com.autopartco.backend.core.questions;

import java.util.UUID;

import com.autopartco.backend.shared.web.PageResponse;

/** Moderación de preguntas por la plataforma. No comprueba permisos (lo hace el llamador). */
public interface QuestionModeration {
    /** @param hidden {@code true} solo ocultas, {@code false} solo visibles, {@code null} todas */
    PageResponse<AdminQuestion> list(Boolean hidden, Integer page, Integer size);

    /**
     * 404 {@code QUESTION_NOT_FOUND}; 409 {@code QUESTION_ALREADY_HIDDEN}; 400 {@code QUESTION_REASON_INVALID} si el
     * motivo (recortado) no tiene entre 3 y 200 caracteres.
     */
    AdminQuestion hide(UUID questionId, String reason);

    /** 404 {@code QUESTION_NOT_FOUND}; 409 {@code QUESTION_NOT_HIDDEN}. */
    AdminQuestion unhide(UUID questionId);
}
