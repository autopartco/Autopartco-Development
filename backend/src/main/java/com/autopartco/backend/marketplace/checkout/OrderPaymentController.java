package com.autopartco.backend.marketplace.checkout;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** Pago del pedido del comprador autenticado (contrato {@code payments-wompi.md} §3 y §4). Solo el dueño. */
@RestController
@AllArgsConstructor
public class OrderPaymentController {
    private static final String PAYMENT = ApiPaths.ME + "/orders/{orderId}/payment";

    private final OrderPaymentStatus orderPaymentStatus;

    @GetMapping(PAYMENT)
    public OrderPaymentView payment(@PathVariable UUID orderId, Authentication authentication) {
        return orderPaymentStatus.get(authentication.getName(), orderId);
    }

    /** Consulta la transacción en Wompi con el {@code id} de la redirección y la aplica si es de este pedido. */
    @PostMapping(PAYMENT + "/sync")
    public OrderPaymentView sync(
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderPaymentSyncRequest request,
            Authentication authentication
    ) {
        return orderPaymentStatus.sync(authentication.getName(), orderId, request.transactionId());
    }
}
