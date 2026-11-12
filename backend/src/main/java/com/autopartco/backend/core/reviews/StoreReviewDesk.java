package com.autopartco.backend.core.reviews;

import java.util.UUID;

import com.autopartco.backend.shared.web.PageResponse;

/**
 * Reseñas de un almacén y de sus productos para su portal. No comprueba permisos (lo hace el llamador). Una reseña
 * de otro almacén responde 404 {@code REVIEW_NOT_FOUND}.
 */
public interface StoreReviewDesk {
    /**
     * Reseñas del almacén y de sus productos, incluidas las ocultas ({@code hidden:true}), más recientes primero.
     * Páginas desde 1.
     *
     * @param type    {@code null} = ambos tipos
     * @param replied {@code null} = con y sin respuesta
     * @param stars   {@code null} = todas (1 a 5)
     */
    PageResponse<PortalReview> list(UUID storeId, ReviewType type, Boolean replied, Integer stars, Integer page,
            Integer size);

    PortalReviewSummary summary(UUID storeId);

    /**
     * Crea o reemplaza la respuesta. 400 {@code VALIDATION_ERROR} si está vacía o pasa de 1000 caracteres y
     * {@code REVIEW_LINKS_NOT_ALLOWED} si incluye enlaces.
     *
     * @param email cuenta que responde (queda como {@code replied_by})
     */
    PortalReview reply(UUID storeId, ReviewType type, UUID reviewId, String email, String text);

    /** Quita la respuesta (sin respuesta previa no cambia nada). */
    void removeReply(UUID storeId, ReviewType type, UUID reviewId);
}
