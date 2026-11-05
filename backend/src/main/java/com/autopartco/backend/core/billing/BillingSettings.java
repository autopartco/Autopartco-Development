package com.autopartco.backend.core.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.autopartco.backend.core.payments.WompiProperties;

/** Configuración de cobros que el portal necesita conocer: si el simulador está encendido y si Wompi está listo. */
@Service
public class BillingSettings {
    private final boolean simulatedPaymentsEnabled;
    private final WompiProperties wompiProperties;

    public BillingSettings(
            @Value("${app.billing.simulated-payments-enabled:false}") boolean simulatedPaymentsEnabled,
            WompiProperties wompiProperties
    ) {
        this.simulatedPaymentsEnabled = simulatedPaymentsEnabled;
        this.wompiProperties = wompiProperties;
    }

    public boolean simulatedPaymentsEnabled() {
        return simulatedPaymentsEnabled;
    }

    /** SIMULATED si el interruptor está encendido; WOMPI si está apagado y Wompi configurado; si no, NONE. */
    public PaymentMode paymentMode() {
        if (simulatedPaymentsEnabled) {
            return PaymentMode.SIMULATED;
        }
        boolean wompiConfigured = wompiProperties != null && wompiProperties.checkoutConfigured();
        return wompiConfigured ? PaymentMode.WOMPI : PaymentMode.NONE;
    }
}
