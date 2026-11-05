package com.autopartco.backend.core.payments;

import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Transacción de Wompi ya validada en su forma (no en su contenido): id, referencia, estado crudo, monto en centavos
 * y moneda. Viene del webhook ({@code data.transaction}) o de la API ({@code GET /v1/transactions…}).
 *
 * @param id            id de la transacción en Wompi
 * @param reference     referencia de Autopartco ({@code APC-…}, {@code APM-…})
 * @param status        estado crudo de Wompi: PENDING, APPROVED, DECLINED, ERROR, VOIDED
 * @param amountInCents monto en centavos enteros
 * @param currency      moneda ISO 4217 (COP)
 * @param raw           JSON original de la transacción; solo para registrar el evento, no para decidir
 */
public record WompiTransaction(
        String id,
        String reference,
        String status,
        long amountInCents,
        String currency,
        JsonNode raw
) {
    public static final String APPROVED = "APPROVED";
    public static final String PENDING = "PENDING";
    public static final String DECLINED = "DECLINED";
    public static final String ERROR = "ERROR";
    public static final String VOIDED = "VOIDED";

    public WompiTransaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(currency, "currency");
    }

    public boolean approved() {
        return APPROVED.equals(status);
    }

    public boolean pending() {
        return PENDING.equals(status);
    }

    /** DECLINED, ERROR o VOIDED: estado final sin cobro. */
    public boolean failed() {
        return DECLINED.equals(status) || ERROR.equals(status) || VOIDED.equals(status);
    }
}
