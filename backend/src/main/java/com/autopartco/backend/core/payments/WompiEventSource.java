package com.autopartco.backend.core.payments;

/** Origen de una transacción aplicada: columna {@code payment_provider_events.source}. */
public enum WompiEventSource {
    /** Evento firmado recibido en {@code POST /api/v1/webhooks/wompi}. */
    WEBHOOK,
    /** Consulta a {@code GET /v1/transactions/{id}} tras la redirección del navegador. */
    REDIRECT_SYNC,
    /** Consulta a {@code GET /v1/transactions?reference=} del trabajo de conciliación. */
    RECONCILIATION
}
