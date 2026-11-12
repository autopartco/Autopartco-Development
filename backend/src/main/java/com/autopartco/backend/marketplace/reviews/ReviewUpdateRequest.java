package com.autopartco.backend.marketplace.reviews;

import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Edición de una reseña propia (almacén o producto) dentro de los 30 días. Reemplaza calificación, comentario y
 * categorías: {@code aspects} nulo o vacío las quita.
 */
public record ReviewUpdateRequest(
        @NotNull(message = "La calificación es obligatoria.")
        @Min(value = 1, message = "La calificación debe estar entre 1 y 5.")
        @Max(value = 5, message = "La calificación debe estar entre 1 y 5.")
        Integer rating,
        @Size(max = 1000, message = "El comentario no puede superar 1000 caracteres.")
        String comment,
        Map<String, Integer> aspects
) {
}
