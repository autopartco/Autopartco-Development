package com.autopartco.backend.marketplace.checkout;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.autopartco.backend.core.payments.PaymentProvider;
import com.autopartco.backend.core.payments.PaymentSettings;

/**
 * Proveedor de pagos simulado: aprueba salvo que el cliente pida {@code REJECTED}. Sin llamadas externas ni
 * aleatoriedad. Solo está activo con {@code APP_CHECKOUT_PAYMENT_PROVIDER=SIMULATED} (el valor por defecto fuera del
 * perfil prod); sustituye a {@code APP_CHECKOUT_SIMULATED_PAYMENTS_ENABLED} (plan Wompi, N1).
 */
@Component
class SimulatedCheckoutPaymentGateway implements CheckoutPaymentGateway {
    private static final String REJECTED = "REJECTED";

    private final boolean enabled;

    @Autowired
    SimulatedCheckoutPaymentGateway(PaymentSettings paymentSettings) {
        this(paymentSettings.checkoutProvider() == PaymentProvider.SIMULATED);
    }

    SimulatedCheckoutPaymentGateway(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public PaymentDecision charge(PaymentAttempt attempt) {
        return REJECTED.equals(attempt.simulatedOutcome()) ? PaymentDecision.REJECTED : PaymentDecision.APPROVED;
    }
}
