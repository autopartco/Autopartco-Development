package com.autopartco.backend.marketplace.reviews;

/**
 * Categorías opcionales (1–5) de una reseña de producto. Deben coincidir con el {@code check} de
 * {@code product_review_aspects.aspect}; las etiquetas e insignias viven en el frontend.
 */
public enum ProductAspect {
    QUALITY,
    FIT,
    AS_DESCRIBED,
    VALUE,
    INSTALL
}
