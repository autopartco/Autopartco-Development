package com.autopartco.backend.marketplace.checkout;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Proveedor de pagos simulado: aprueba salvo que el cliente pida {@code REJECTED}. Sin llamadas externas ni
 * aleatoriedad. Se apaga con {@code app.checkout.simulated-payments-enabled=false} (activo por defecto).
 */
@Component
class SimulatedCheckoutPaymentGateway implements CheckoutPaymentGateway {
    private static final String REJECTED = "REJECTED";

    private final boolean enabled;

    SimulatedCheckoutPaymentGateway(@Value("${app.checkout.simulated-payments-enabled:true}") boolean enabled) {
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
