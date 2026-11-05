package com.autopartco.backend.core.payments;

/** Resultado de aplicar una transacción: columna {@code payment_provider_events.outcome} (ADR 0004 §2.7). */
public enum WompiEventOutcome {
    /** Cambió el estado del pago (o del pedido/suscripción). */
    APPLIED,
    /** Evento ya registrado (mismo checksum): sin efectos. No se inserta una fila nueva. */
    DUPLICATE,
    /** Válido pero sin cambios: estado intermedio, repetición o estado desconocido. */
    IGNORED,
    /** Monto, moneda o entorno distintos, o transacción ya asociada a otro pago. Log ERROR. */
    REJECTED_MISMATCH,
    /** Ningún pago con esa referencia. Log ERROR. */
    UNKNOWN_REFERENCE
}
