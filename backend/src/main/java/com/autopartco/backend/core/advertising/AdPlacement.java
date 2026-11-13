package com.autopartco.backend.core.advertising;

import java.util.Locale;
import java.util.Optional;

/** Ubicación de un anuncio (§2): columna {@code ad_placement_prices.placement} y elementos de {@code ad_campaigns.placements}. */
public enum AdPlacement {
    /** Primeras posiciones de búsqueda o categoría; 2 por página. */
    SEARCH_TOP(2),
    /** Carrusel «Destacados» del inicio; 12 rotativos. */
    HOME_FEATURED(12),
    /** Cabecera de la página de categoría; 3. */
    CATEGORY_TOP(3),
    /** «También te puede servir» en el detalle de producto; 4. */
    PRODUCT_RELATED(4),
    /** Almacén «Recomendado» en el mapa de cobertura del inicio; 3 almacenes por área. Campaña sin productos. */
    MAP_FEATURED(3);

    private final int slots;

    AdPlacement(int slots) {
        this.slots = slots;
    }

    /** Cupo de anuncios por página en esta ubicación. */
    public int slots() {
        return slots;
    }

    /** Vacío si el valor no es una ubicación conocida. */
    public static Optional<AdPlacement> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }
}
