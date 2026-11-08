package com.autopartco.backend.marketplace.reviews;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StoreReviewRequest(
        @NotNull(message = "Indica el pedido que quieres calificar.")
        UUID orderId,
        @NotNull(message = "La calificación es obligatoria.")
        @Min(value = 1, message = "La calificación debe estar entre 1 y 5.")
        @Max(value = 5, message = "La calificación debe estar entre 1 y 5.")
        Integer rating,
        @Size(max = 1000, message = "El comentario no puede superar 1000 caracteres.")
        String comment
) {
}
