package com.autopartco.backend.marketplace.reviews;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code aspects}: códigos de {@link StoreAspect} → 1–5, opcional (400 {@code REVIEW_ASPECT_INVALID} si no). */
public record StoreReviewRequest(
        @NotNull(message = "Indica el pedido que quieres calificar.")
        UUID orderId,
        @NotNull(message = "La calificación es obligatoria.")
        @Min(value = 1, message = "La calificación debe estar entre 1 y 5.")
        @Max(value = 5, message = "La calificación debe estar entre 1 y 5.")
        Integer rating,
        @Size(max = 1000, message = "El comentario no puede superar 1000 caracteres.")
        String comment,
        Map<String, Integer> aspects
) {
    public StoreReviewRequest(UUID orderId, Integer rating, String comment) {
        this(orderId, rating, comment, null);
    }
}
