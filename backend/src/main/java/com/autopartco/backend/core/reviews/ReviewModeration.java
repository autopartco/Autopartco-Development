package com.autopartco.backend.core.reviews;

import java.util.UUID;

import com.autopartco.backend.shared.web.PageResponse;

/**
 * Moderación de reseñas por la plataforma. No comprueba permisos (lo hace el llamador). Ocultar o mostrar publica
 * {@code ListingsChanged} del producto o del almacén y vacía el perfil público cacheado.
 */
public interface ReviewModeration {
    /**
     * @param hidden {@code true} solo ocultas, {@code false} solo visibles, {@code null} todas
     * @param type   {@code null} = ambos tipos
     */
    PageResponse<AdminReview> list(Boolean hidden, ReviewType type, Integer page, Integer size);

    /**
     * 404 {@code REVIEW_NOT_FOUND}; 409 {@code REVIEW_ALREADY_HIDDEN}; 400 {@code REVIEW_REASON_INVALID} si el motivo
     * (recortado) no tiene entre 3 y 200 caracteres.
     */
    AdminReview hide(ReviewType type, UUID reviewId, String reason);

    /** 404 {@code REVIEW_NOT_FOUND}; 409 {@code REVIEW_NOT_HIDDEN}. */
    AdminReview unhide(ReviewType type, UUID reviewId);
}
