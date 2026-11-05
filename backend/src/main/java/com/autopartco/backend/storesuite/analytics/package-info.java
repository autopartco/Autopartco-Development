/**
 * Inteligencia comercial del almacén: reporte de ventas, segmentación de clientes, rendimiento de productos y
 * pronóstico. Todos los casos de uso exigen el permiso {@code ANALYTICS_READ} y después la capacidad
 * {@code ADVANCED_REPORTS} del plan (si falta, 409 {@code SELLER_PLAN_FEATURE_UNAVAILABLE}; si venció la
 * suscripción, 409 {@code SELLER_SUBSCRIPTION_EXPIRED}). Los cálculos viven en {@code core.analytics}.
 */
package com.autopartco.backend.storesuite.analytics;
