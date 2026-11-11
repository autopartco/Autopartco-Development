package com.autopartco.backend.marketplace.checkout;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.order.BuyerOrderResponse;
import com.autopartco.backend.core.payments.PaymentProvider;
import com.autopartco.backend.core.payments.PaymentSettings;
import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.VisitorCookie;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** Checkout del comprador autenticado (la ruta no es pública). */
@RestController
@AllArgsConstructor
public class MarketplaceOrderController {
    private static final String ORDERS = ApiPaths.MARKETPLACE + "/orders";
    private static final String CHECKOUT_OPTIONS = ApiPaths.MARKETPLACE + "/checkout-options";
    private static final String BUYER_ORDERS = ApiPaths.ME + "/orders/";

    private final PlaceMarketplaceOrder placeMarketplaceOrder;
    private final StartMarketplaceCheckout startMarketplaceCheckout;
    private final CheckoutOptions checkoutOptions;
    private final PaymentSettings paymentSettings;

    /** Proveedor activo y métodos de pago (contrato {@code payments-wompi.md} §1). */
    @GetMapping(CHECKOUT_OPTIONS)
    public CheckoutOptionsResponse checkoutOptions() {
        return checkoutOptions.current();
    }

    /**
     * SIMULATED: 201 con {@code Location} y el recibo, o 200 con el mismo cuerpo si es una repetición.
     * WOMPI: 202 con {@code Location} y el enlace de pago, o 200 con la misma forma si es una repetición (§2.2).
     */
    @PostMapping(ORDERS)
    public ResponseEntity<?> place(
            @Valid @RequestBody PlaceOrderRequest request,
            Authentication authentication,
            HttpServletRequest http
    ) {
        return place(withVisitorCookie(request, http), authentication);
    }

    /**
     * Visitante del pedido para atribuir anuncios: la cookie HttpOnly {@code ap_aid} (la emite {@code /events/*})
     * tiene prioridad, porque es el mismo id con el que {@code /events/ads} registra los clics; el {@code anonymousId}
     * del cuerpo solo se usa si no llega la cookie (compatibilidad). Sin ninguno, el pedido no se atribuye.
     */
    static PlaceOrderRequest withVisitorCookie(PlaceOrderRequest request, HttpServletRequest http) {
        return VisitorCookie.read(http)
                .filter(id -> !id.equals(request.anonymousId()))
                .map(id -> new PlaceOrderRequest(request.clientRequestId(), request.addressId(),
                        request.paymentMethod(), request.simulatedOutcome(), request.items(), id))
                .orElse(request);
    }

    public ResponseEntity<?> place(PlaceOrderRequest request, Authentication authentication) {
        if (paymentSettings.checkoutProvider() == PaymentProvider.WOMPI) {
            WompiCheckoutResult result = startMarketplaceCheckout.execute(authentication.getName(), request);
            WompiCheckoutResponse body = result.response();
            HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.ACCEPTED;
            return ResponseEntity.status(status).location(buyerOrder(body.orderId())).body(body);
        }
        CheckoutResult result = placeMarketplaceOrder.execute(authentication.getName(), request);
        BuyerOrderResponse order = result.order();
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .location(buyerOrder(order.orderId()))
                .body(order);
    }

    private static URI buyerOrder(UUID orderId) {
        return URI.create(BUYER_ORDERS + orderId);
    }
}
