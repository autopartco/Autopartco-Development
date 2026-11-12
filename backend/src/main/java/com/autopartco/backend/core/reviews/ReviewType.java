package com.autopartco.backend.core.reviews;

import java.util.Locale;

import com.autopartco.backend.shared.error.BadRequestException;

/** Tipo de reseña: de un almacén o de un producto. En las rutas va en minúsculas ({@code store|product}). */
public enum ReviewType {
    STORE,
    PRODUCT;

    /** {@code null} o vacío da {@code null} (todos). Otro valor: 400 {@code REVIEW_TYPE_INVALID}. */
    public static ReviewType parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return parse(raw);
    }

    /** 400 {@code REVIEW_TYPE_INVALID} con un valor distinto de {@code store} o {@code product}. */
    public static ReviewType parse(String raw) {
        try {
            return valueOf(raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new BadRequestException("REVIEW_TYPE_INVALID", "El tipo de reseña debe ser store o product.");
        }
    }

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
