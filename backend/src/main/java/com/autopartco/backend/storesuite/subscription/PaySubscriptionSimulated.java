package com.autopartco.backend.storesuite.subscription;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.billing.SimulatedPaymentRequest;
import com.autopartco.backend.core.billing.SimulatedPaymentResult;
import com.autopartco.backend.core.billing.SimulatedSubscriptionPayments;

import lombok.AllArgsConstructor;

/** Cobro simulado de la suscripción. Solo el OWNER (lo verifica {@link SimulatedSubscriptionPayments}). */
@Service
@AllArgsConstructor
public class PaySubscriptionSimulated {
    private final SimulatedSubscriptionPayments simulatedPayments;
    private final GetSubscription getSubscription;

    /** {@code created} indica 201 (nuevo) o 200 (repetición idempotente). */
    public record Outcome(SimulatedPaymentResponse body, boolean created) {
    }

    @Transactional
    public Outcome execute(UUID storeId, String email, SimulatedPaymentRequest request) {
        SimulatedPaymentResult result = simulatedPayments.pay(storeId, email, request);
        return new Outcome(
                new SimulatedPaymentResponse(result.payment(), getSubscription.assemble(storeId, email)),
                result.created()
        );
    }
}
