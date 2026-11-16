/**
 * Modelos predictivos entrenados fuera del backend ({@code ml/}, Python) y ejecutados aquí en Java puro, sin
 * librerías nativas: cada modelo es un ensamble de árboles en {@code classpath:ml/<clave>/model.json} y sus variables
 * salen de {@code classpath:ml/<clave>/features.sql}, el mismo SQL con el que se entrenó.
 *
 * <ul>
 *   <li>{@code demand_category_28d}: LightGBM Poisson, unidades de los próximos 28 días por sucursal y categoría.</li>
 *   <li>{@code stockout_14d}: Random Forest calibrado, probabilidad de agotarse en 14 días por inventario.</li>
 *   <li>{@code payment_anomaly}: Isolation Forest, pagos atípicos para revisión.</li>
 * </ul>
 *
 * <p>Ninguna petición ejecuta un modelo: {@link com.autopartco.backend.core.ml.MlScoringJobs} puntúa en lote y guarda
 * en {@code ml.predictions} y {@code payment_risk_assessments}; {@link com.autopartco.backend.core.ml.MlPredictions}
 * lee el último lote. No comprueba permisos: lo hacen los casos de uso.</p>
 */
@ApplicationModule(displayName = "Core: ML", allowedDependencies = "shared")
package com.autopartco.backend.core.ml;

import org.springframework.modulith.ApplicationModule;
