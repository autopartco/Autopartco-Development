package com.autopartco.backend.marketplace.checkout;

/** Resultado del cobro devuelto por el {@link CheckoutPaymentGateway}. */
public enum PaymentDecision {
    APPROVED,
    REJECTED
}
