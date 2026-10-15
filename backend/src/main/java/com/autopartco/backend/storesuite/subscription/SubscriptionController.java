package com.autopartco.backend.storesuite.subscription;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.billing.CheckoutRequest;
import com.autopartco.backend.core.billing.CheckoutResponse;
import com.autopartco.backend.core.billing.SimulatedPaymentRequest;
import com.autopartco.backend.core.billing.SubscriptionCheckout;
import com.autopartco.backend.core.billing.SubscriptionPaymentView;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * Suscripción del almacén: estado, historial de pagos, checkout de Wompi y cobro simulado. El checkout y el cobro
 * simulado solo los puede iniciar el OWNER (regla que aplican {@link SubscriptionCheckout} y el cobro simulado).
 */
@RestController
@RequestMapping(ApiPaths.STORES + "/{storeId}/subscription")
@AllArgsConstructor
public class SubscriptionController {
    private final SubscriptionCheckout subscriptionCheckout;
    private final GetSubscription getSubscription;
    private final ListSubscriptionPayments listSubscriptionPayments;
    private final PaySubscriptionSimulated paySubscriptionSimulated;

    @GetMapping
    public SubscriptionResponse subscription(@PathVariable UUID storeId, Authentication authentication) {
        return getSubscription.execute(storeId, authentication.getName());
    }

    @GetMapping("/payments")
    public List<SubscriptionPaymentView> payments(@PathVariable UUID storeId, Authentication authentication) {
        return listSubscriptionPayments.execute(storeId, authentication.getName());
    }

    @PostMapping("/checkout")
    public CheckoutResponse checkout(
            @PathVariable UUID storeId,
            @Valid @RequestBody CheckoutRequest request,
            Authentication authentication
    ) {
        return subscriptionCheckout.createCheckout(storeId, authentication.getName(), request);
    }

    @PostMapping("/simulated-payments")
    public ResponseEntity<SimulatedPaymentResponse> simulatedPayment(
            @PathVariable UUID storeId,
            @Valid @RequestBody SimulatedPaymentRequest request,
            Authentication authentication
    ) {
        PaySubscriptionSimulated.Outcome outcome =
                paySubscriptionSimulated.execute(storeId, authentication.getName(), request);
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK).body(outcome.body());
    }
}
