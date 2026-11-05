package com.autopartco.backend.core.payments;

import java.util.Locale;
import java.util.Optional;

/** Proveedor de cobro del checkout del marketplace ({@code APP_CHECKOUT_PAYMENT_PROVIDER}). */
public enum PaymentProvider {
    SIMULATED,
    WOMPI;

    /** Vacío si el valor está en blanco o no es un proveedor conocido. */
    public static Optional<PaymentProvider> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
