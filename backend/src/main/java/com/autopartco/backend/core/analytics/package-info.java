/**
 * Inteligencia comercial: lecturas JDBC de ventas, clientes, productos y búsquedas del marketplace, y lógica pura de
 * segmentación de clientes, clasificación ABC/XYZ, stock mínimo sugerido y pronóstico de ventas. También registra las
 * búsquedas del marketplace ({@code marketplace_searches}, sin datos personales) y aplica su retención.
 * No comprueba permisos: lo hacen los casos de uso de {@code storesuite.analytics} y {@code operations.metrics}.
 *
 * <p>Venta válida: estado {@code CONFIRMED}, {@code PREPARING}, {@code SHIPPED} o {@code DELIVERED} y sin borrar.
 * Fechas en la zona {@code America/Bogota}.</p>
 */
@ApplicationModule(displayName = "Core: Analytics", allowedDependencies = "shared")
package com.autopartco.backend.core.analytics;

import org.springframework.modulith.ApplicationModule;
