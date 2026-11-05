package com.autopartco.backend.core.billing.internal;

import org.springframework.context.annotation.Configuration;

/**
 * Configuración del dominio de cobros de suscripción. Las propiedades de Wompi ({@code wompi.*}) y la activación de
 * {@code @Scheduled} (que usa {@code SubscriptionPaymentReconciler}) las registra {@code core.payments}.
 */
@Configuration
public class BillingConfig {
}
