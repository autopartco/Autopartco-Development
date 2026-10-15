package com.autopartco.backend.core.billing;

/** Resultado de un cobro simulado; {@code created} es falso si fue una repetición idempotente. */
public record SimulatedPaymentResult(SubscriptionPaymentView payment, boolean created) {
}
