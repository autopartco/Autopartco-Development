package com.autopartco.backend.marketplace.reviews;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Nueva reseña de producto. {@code orderId}: un pedido propio con el producto entregado. {@code aspects}: códigos de
 * {@link ProductAspect} → 1–5, opcional.
 */
public record ProductReviewRequest(
        @NotNull(message = "Indica el pedido en el que recibiste el producto.")
        UUID orderId,
        @NotNull(message = "La calificación es obligatoria.")
        @Min(value = 1, message = "La calificación debe estar entre 1 y 5.")
        @Max(value = 5, message = "La calificación debe estar entre 1 y 5.")
        Integer rating,
        @Size(max = 1000, message = "El comentario no puede superar 1000 caracteres.")
        String comment,
        Map<String, Integer> aspects
) {
}
