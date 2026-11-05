package com.autopartco.backend.marketplace.checkout;

/** Resultado del inicio del pago Wompi; {@code replayed} es {@code true} si el {@code clientRequestId} ya existía. */
public record WompiCheckoutResult(WompiCheckoutResponse response, boolean replayed) {
}
