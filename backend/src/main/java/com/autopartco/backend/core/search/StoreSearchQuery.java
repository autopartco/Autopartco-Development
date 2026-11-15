package com.autopartco.backend.core.search;

import com.autopartco.backend.shared.error.BadRequestException;

/**
 * Búsqueda de almacenes: texto libre, municipio DIVIPOLA (5 dígitos) de una sucursal activa y página (desde 0).
 * {@code size} de 1 a {@value #MAX_SIZE} (por defecto {@value #DEFAULT_SIZE}).
 */
public record StoreSearchQuery(String q, String cityCode, Integer page, Integer size) {
    public static final int DEFAULT_SIZE = 12;
    public static final int MAX_SIZE = 24;

    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : size;
    }

    /** {@code SEARCH_PAGE_INVALID}, {@code SEARCH_SIZE_INVALID} o {@code SEARCH_PLACE_INVALID} (400). */
    public void validate() {
        if (pageOrDefault() < 0 || pageOrDefault() > SearchQuery.MAX_PAGE) {
            throw new BadRequestException("SEARCH_PAGE_INVALID",
                    "La página debe estar entre 0 y " + SearchQuery.MAX_PAGE + ".");
        }
        if (sizeOrDefault() < 1 || sizeOrDefault() > MAX_SIZE) {
            throw new BadRequestException("SEARCH_SIZE_INVALID",
                    "El tamaño de página debe estar entre 1 y " + MAX_SIZE + ".");
        }
        if (cityCode != null && !cityCode.isBlank() && !cityCode.strip().matches("\\d{5}")) {
            throw new BadRequestException("SEARCH_PLACE_INVALID", "La ciudad (5 dígitos) no tiene el formato DIVIPOLA.");
        }
    }

    /** Código de municipio sin espacios o {@code ""} si no se filtra. */
    String city() {
        return cityCode == null ? "" : cityCode.strip();
    }

    /** Texto normalizado (minúsculas, sin tildes, {@code [a-z0-9 ]}, hasta 120 caracteres) o {@code ""}. */
    String normalizedText() {
        return StoreSearchSql.normalize(q);
    }
}
