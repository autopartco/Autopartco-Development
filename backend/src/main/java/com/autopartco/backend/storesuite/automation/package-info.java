/**
 * Alertas automáticas del almacén (caída de ventas, agotamiento, cliente frecuente en riesgo, stock sin movimiento).
 * La tarea nocturna ({@code StoreAlertJob}, 05:00 America/Bogota) evalúa las reglas de
 * {@code core.analytics.AlertRules} y guarda en {@code store_alerts}; los casos de uso exponen lectura y cambio de
 * estado ({@code STORE_READ}) y preferencias ({@code ALERT_MANAGE}). Todas las dependencias ya están permitidas por
 * el módulo {@code storesuite}.
 */
package com.autopartco.backend.storesuite.automation;
