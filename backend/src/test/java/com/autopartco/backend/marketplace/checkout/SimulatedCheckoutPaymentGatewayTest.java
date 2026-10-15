package com.autopartco.backend.marketplace.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class SimulatedCheckoutPaymentGatewayTest {

    private static PaymentAttempt attempt(String outcome) {
        return new PaymentAttempt("SIMULATED_CARD", new BigDecimal("230000.00"), "COP", "ref-1", outcome);
    }

    @Test
    void approvesByDefault() {
        SimulatedCheckoutPaymentGateway gateway = new SimulatedCheckoutPaymentGateway(true);

        assertThat(gateway.charge(attempt(null))).isEqualTo(PaymentDecision.APPROVED);
        assertThat(gateway.charge(attempt("APPROVED"))).isEqualTo(PaymentDecision.APPROVED);
    }

    @Test
    void rejectsWhenRequested() {
        SimulatedCheckoutPaymentGateway gateway = new SimulatedCheckoutPaymentGateway(true);

        assertThat(gateway.charge(attempt("REJECTED"))).isEqualTo(PaymentDecision.REJECTED);
    }

    @Test
    void outcomeIsDeterministic() {
        SimulatedCheckoutPaymentGateway gateway = new SimulatedCheckoutPaymentGateway(true);

        for (int i = 0; i < 20; i++) {
            assertThat(gateway.charge(attempt("APPROVED"))).isEqualTo(PaymentDecision.APPROVED);
            assertThat(gateway.charge(attempt("REJECTED"))).isEqualTo(PaymentDecision.REJECTED);
        }
    }

    @Test
    void reportsSwitchState() {
        assertThat(new SimulatedCheckoutPaymentGateway(true).enabled()).isTrue();
        assertThat(new SimulatedCheckoutPaymentGateway(false).enabled()).isFalse();
    }
}
