package com.autopartco.backend.marketplace.reviews;

/**
 * Categorías opcionales (1–5) de una reseña de almacén. Deben coincidir con el {@code check} de
 * {@code store_review_aspects.aspect}; las etiquetas e insignias viven en el frontend.
 */
public enum StoreAspect {
    SERVICE,
    SPEED,
    QUALITY,
    PRICE,
    ACCURACY
}
