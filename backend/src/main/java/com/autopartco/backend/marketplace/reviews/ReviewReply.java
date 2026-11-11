package com.autopartco.backend.marketplace.reviews;

import java.time.OffsetDateTime;

/** Respuesta pública del almacén a una reseña (la escribe el portal). */
public record ReviewReply(String text, OffsetDateTime repliedAt) {
    static ReviewReply of(String text, OffsetDateTime repliedAt) {
        return text == null ? null : new ReviewReply(text, repliedAt);
    }
}
