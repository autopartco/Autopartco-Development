/**
 * Puertos de gestión de reseñas para los productos de almacén y de operaciones: responder y consultar desde el portal
 * ({@link com.autopartco.backend.core.reviews.StoreReviewDesk}) y moderar desde administración
 * ({@link com.autopartco.backend.core.reviews.ReviewModeration}). Los implementa {@code marketplace.reviews}, dueño de
 * las tablas; este módulo solo define contratos para que {@code storesuite} y {@code operations} no dependan del
 * marketplace.
 */
@ApplicationModule(displayName = "Core: Reviews", allowedDependencies = "shared")
package com.autopartco.backend.core.reviews;

import org.springframework.modulith.ApplicationModule;
