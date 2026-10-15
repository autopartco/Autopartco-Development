package com.autopartco.backend.marketplace.checkout;

import com.autopartco.backend.core.order.BuyerOrderResponse;

/** Pedido resultante; {@code replayed} es {@code true} si ya existía con el mismo {@code clientRequestId}. */
public record CheckoutResult(BuyerOrderResponse order, boolean replayed) {
}
