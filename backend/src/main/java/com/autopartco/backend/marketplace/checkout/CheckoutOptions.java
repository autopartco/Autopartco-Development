package com.autopartco.backend.marketplace.checkout;

import java.util.List;

import org.springframework.stereotype.Service;

import com.autopartco.backend.core.order.OrderPlacement;
import com.autopartco.backend.core.payments.PaymentProvider;
import com.autopartco.backend.core.payments.PaymentSettings;

import lombok.AllArgsConstructor;

/**
 * Proveedor activo del checkout y sus métodos, para que el frontend no codifique los métodos simulados (N12). Con
 * SIMULATED: los métodos {@code SIMULATED_*} activos. Con WOMPI: el método {@code WOMPI} si está activo.
 */
@Service
@AllArgsConstructor
class CheckoutOptions {
    static final String SIMULATED_PREFIX = "SIMULATED_";
    static final String WOMPI_NAME = "Wompi: tarjeta, PSE, Nequi, Bancolombia";

    private final PaymentSettings paymentSettings;
    private final OrderPlacement orderPlacement;

    CheckoutOptionsResponse current() {
        if (paymentSettings.checkoutProvider() == PaymentProvider.WOMPI) {
            List<CheckoutOptionsResponse.Method> methods =
                    orderPlacement.isActivePaymentMethod(StartMarketplaceCheckout.WOMPI_METHOD)
                            ? List.of(new CheckoutOptionsResponse.Method(StartMarketplaceCheckout.WOMPI_METHOD,
                                    WOMPI_NAME))
                            : List.of();
            return new CheckoutOptionsResponse(
                    PaymentProvider.WOMPI.name(), paymentSettings.wompiEnvironment().name(), methods);
        }
        return new CheckoutOptionsResponse(
                PaymentProvider.SIMULATED.name(),
                null,
                orderPlacement.activePaymentMethods(SIMULATED_PREFIX).stream()
                        .map(method -> new CheckoutOptionsResponse.Method(method.code(), method.name()))
                        .toList());
    }
}
