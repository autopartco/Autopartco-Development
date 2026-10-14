package com.autopartco.backend.storesuite.subscription;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.billing.CheckoutRequest;
import com.autopartco.backend.core.billing.CheckoutResponse;
import com.autopartco.backend.core.billing.SubscriptionCheckout;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * Pago de la suscripción del almacén. Solo el solicitante de la solicitud del almacén puede iniciar el checkout
 * (regla que aplica {@link SubscriptionCheckout}).
 */
@RestController
@RequestMapping(ApiPaths.STORES + "/{storeId}/subscription")
@AllArgsConstructor
public class SubscriptionController {
    private final SubscriptionCheckout subscriptionCheckout;

    @PostMapping("/checkout")
    public CheckoutResponse checkout(
            @PathVariable UUID storeId,
            @Valid @RequestBody CheckoutRequest request,
            Authentication authentication
    ) {
        return subscriptionCheckout.createCheckout(storeId, authentication.getName(), request);
    }
}
