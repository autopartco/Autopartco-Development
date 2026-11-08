package com.autopartco.backend.marketplace.reviews;

import java.math.BigDecimal;

/** Promedio con un decimal ({@code null} sin reseñas) y número de reseñas. */
public record StoreRating(BigDecimal average, long count) {
    public static final StoreRating NONE = new StoreRating(null, 0);
}
