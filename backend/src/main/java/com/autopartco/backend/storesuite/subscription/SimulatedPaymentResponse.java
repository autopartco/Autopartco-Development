package com.autopartco.backend.storesuite.subscription;

import com.autopartco.backend.core.billing.SubscriptionPaymentView;

/** Respuesta del cobro simulado: el pago registrado y la suscripción resultante. */
public record SimulatedPaymentResponse(SubscriptionPaymentView payment, SubscriptionResponse subscription) {
}
