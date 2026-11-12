package com.autopartco.backend.core.questions;

import java.util.Collection;
import java.util.UUID;

import com.autopartco.backend.shared.web.PageResponse;

/**
 * Preguntas sobre los productos de un almacén para su portal. No comprueba permisos (lo hace el llamador), pero sí el
 * alcance: {@code branchIds} limita a los productos con inventario en alguna de esas sucursales del almacén
 * ({@code null} = todo el almacén). Una pregunta de otro almacén o fuera del alcance responde 404
 * {@code QUESTION_NOT_FOUND}.
 */
public interface StoreQuestionDesk {
    /**
     * Preguntas no borradas, incluidas las ocultas ({@code hidden:true}), más recientes primero. Páginas desde 1.
     *
     * @param answered {@code null} = respondidas y pendientes
     */
    PageResponse<PortalQuestion> list(UUID storeId, Boolean answered, Collection<UUID> branchIds, Integer page,
            Integer size);

    /** Preguntas visibles (no ocultas ni borradas) todavía sin respuesta dentro del alcance. */
    long pending(UUID storeId, Collection<UUID> branchIds);

    /**
     * Crea o reemplaza la respuesta. 400 {@code VALIDATION_ERROR} si está vacía o pasa de 1000 caracteres y
     * {@code QUESTION_CONTACT_NOT_ALLOWED} si incluye enlaces, correos o teléfonos. La primera respuesta avisa al autor.
     *
     * @param email cuenta que responde (queda como {@code answered_by})
     */
    PortalQuestion answer(UUID storeId, UUID questionId, Collection<UUID> branchIds, String email, String text);

    /** Quita la respuesta (sin respuesta previa no cambia nada). */
    void removeAnswer(UUID storeId, UUID questionId, Collection<UUID> branchIds);
}
