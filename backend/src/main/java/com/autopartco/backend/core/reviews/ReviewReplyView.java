package com.autopartco.backend.core.reviews;

import java.time.OffsetDateTime;

/** Respuesta del almacén a una reseña. */
public record ReviewReplyView(String text, OffsetDateTime repliedAt) {
}
