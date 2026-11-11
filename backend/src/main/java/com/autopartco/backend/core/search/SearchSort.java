package com.autopartco.backend.core.search;

import java.util.Locale;

import com.autopartco.backend.shared.error.BadRequestException;

/** Orden de los resultados. {@link #NAME} es interno (compatibilidad de {@code /marketplace/products}). */
public enum SearchSort {
    RELEVANCE,
    PRICE_ASC,
    PRICE_DESC,
    NEAREST,
    NEWEST,
    RATING,
    NAME;

    /** Valor público del parámetro {@code sort} ({@code relevance} por defecto); otro valor: 400 {@code SEARCH_SORT_INVALID}. */
    public static SearchSort fromParameter(String value) {
        if (value == null || value.isBlank()) {
            return RELEVANCE;
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        for (SearchSort sort : values()) {
            if (sort != NAME && sort.name().equals(normalized)) {
                return sort;
            }
        }
        throw new BadRequestException("SEARCH_SORT_INVALID",
                "El orden debe ser relevance, price_asc, price_desc, nearest, newest o rating.");
    }
}
