package com.autopartco.backend.marketplace.checkout;

import java.util.List;

/**
 * {@code GET /api/v1/marketplace/checkout-options} (contrato §1).
 *
 * @param provider    SIMULATED | WOMPI
 * @param environment SANDBOX | PRODUCTION con WOMPI; {@code null} con SIMULATED
 */
public record CheckoutOptionsResponse(String provider, String environment, List<Method> methods) {

    public record Method(String code, String name) {
    }
}
