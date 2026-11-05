package com.autopartco.backend.core.payments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Configuración de cobros ya validada al arrancar ({@code WompiConfigurationValidator}): proveedor del checkout del
 * marketplace, entorno de Wompi y minutos de reserva del stock.
 */
@Service
public class PaymentSettings {
    private final PaymentProvider checkoutProvider;
    private final WompiEnvironment wompiEnvironment;
    private final int reservationMinutes;

    public PaymentSettings(
            @Value("${app.checkout.payment-provider:}") String checkoutProvider,
            @Value("${app.checkout.reservation-minutes:30}") int reservationMinutes,
            WompiProperties wompiProperties
    ) {
        // Sin valor fuera de prod: SIMULATED. En prod el validador exige un valor explícito.
        this.checkoutProvider = PaymentProvider.parse(checkoutProvider).orElse(PaymentProvider.SIMULATED);
        this.wompiEnvironment = wompiProperties.environment();
        this.reservationMinutes = reservationMinutes;
    }

    public PaymentProvider checkoutProvider() {
        return checkoutProvider;
    }

    public WompiEnvironment wompiEnvironment() {
        return wompiEnvironment;
    }

    public int reservationMinutes() {
        return reservationMinutes;
    }
}
