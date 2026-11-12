/**
 * Puertos de gestión de preguntas de producto para los productos de almacén y de operaciones: responder y consultar
 * desde el portal ({@link com.autopartco.backend.core.questions.StoreQuestionDesk}) y moderar desde administración
 * ({@link com.autopartco.backend.core.questions.QuestionModeration}). Los implementa {@code marketplace.questions},
 * dueño de {@code product_questions}; este módulo solo define contratos para que {@code storesuite} y
 * {@code operations} no dependan del marketplace.
 */
@ApplicationModule(displayName = "Core: Questions", allowedDependencies = "shared")
package com.autopartco.backend.core.questions;

import org.springframework.modulith.ApplicationModule;
