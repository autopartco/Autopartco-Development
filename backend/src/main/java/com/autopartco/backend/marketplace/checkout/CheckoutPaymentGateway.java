package com.autopartco.backend.marketplace.checkout;

/**
 * Puerto hacia el proveedor de pagos del checkout. Hoy solo existe el simulador; un proveedor real (Wompi) no debe
 * reutilizar este flujo síncrono dentro de la transacción del pedido.
 */
public interface CheckoutPaymentGateway {

    /** {@code false} si el proveedor está apagado; el caso de uso responde {@code PAYMENT_METHOD_NOT_SUPPORTED}. */
    boolean enabled();

    /** Cobra el intento y devuelve la decisión. Un rechazo no lanza excepción. */
    PaymentDecision charge(PaymentAttempt attempt);
}
