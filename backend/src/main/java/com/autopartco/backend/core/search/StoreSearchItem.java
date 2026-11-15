package com.autopartco.backend.core.search;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.autopartco.backend.core.organization.BusinessType;

/**
 * Almacén publicado en los resultados de búsqueda: solo datos públicos. {@code description} sale recortada a
 * {@value #DESCRIPTION_MAX} caracteres; {@code cities} son las de las sucursales activas (sin repetir, ordenadas);
 * {@code rating.average} es nulo sin reseñas visibles.
 */
public record StoreSearchItem(
        UUID id,
        String name,
        String logoUrl,
        String description,
        List<String> cities,
        int branchCount,
        Rating rating,
        BusinessType businessType,
        long publishedProducts,
        long activeServices
) {
    public static final int DESCRIPTION_MAX = 160;

    public StoreSearchItem {
        cities = cities == null ? List.of() : List.copyOf(cities);
    }

    public record Rating(BigDecimal average, long count) {
    }

    /** Texto en una línea de hasta {@value #DESCRIPTION_MAX} caracteres (con «…» si se recorta) o {@code null}. */
    static String shorten(String description) {
        if (description == null) {
            return null;
        }
        String text = description.strip().replaceAll("\\s+", " ");
        if (text.isEmpty()) {
            return null;
        }
        return text.length() <= DESCRIPTION_MAX ? text : text.substring(0, DESCRIPTION_MAX - 1).stripTrailing() + "…";
    }
}
