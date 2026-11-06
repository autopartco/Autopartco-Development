package com.autopartco.backend.storesuite.automation;

/** Contadores del encabezado: alertas abiertas sin leer y alertas abiertas críticas. */
public record AlertSummaryResponse(long unread, long critical) {
}
