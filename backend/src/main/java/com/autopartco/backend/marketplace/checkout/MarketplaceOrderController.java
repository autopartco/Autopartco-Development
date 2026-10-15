package com.autopartco.backend.marketplace.checkout;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.order.BuyerOrderResponse;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** Checkout del comprador autenticado (la ruta no es pública). */
@RestController
@AllArgsConstructor
public class MarketplaceOrderController {
    private static final String ORDERS = ApiPaths.MARKETPLACE + "/orders";
    private static final String BUYER_ORDERS = ApiPaths.ME + "/orders/";

    private final PlaceMarketplaceOrder placeMarketplaceOrder;

    /** 201 con {@code Location} si se creó el pedido; 200 con el mismo cuerpo si es una repetición idempotente. */
    @PostMapping(ORDERS)
    public ResponseEntity<BuyerOrderResponse> place(
            @Valid @RequestBody PlaceOrderRequest request,
            Authentication authentication
    ) {
        CheckoutResult result = placeMarketplaceOrder.execute(authentication.getName(), request);
        BuyerOrderResponse order = result.order();
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .location(URI.create(BUYER_ORDERS + order.orderId()))
                .body(order);
    }
}
