package com.autopartco.backend.core.payments.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import com.autopartco.backend.core.payments.PaymentProvider;
import com.autopartco.backend.core.payments.WompiEnvironment;
import com.autopartco.backend.core.payments.WompiProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Valida la configuración de cobros al crear el contexto; si algo no cuadra, el arranque falla (ADR 0004 §2.3). Los
 * mensajes nombran variables, nunca sus valores.
 * <ul>
 *   <li>Llaves con el prefijo del entorno: {@code pub_test_}/{@code prv_test_} en SANDBOX y
 *       {@code pub_prod_}/{@code prv_prod_} en PRODUCTION.</li>
 *   <li>{@code APP_CHECKOUT_RESERVATION_MINUTES} entre 5 y 120.</li>
 *   <li>Perfil {@code prod}: {@code WOMPI_ENVIRONMENT=PRODUCTION} salvo opt-in explícito
 *       {@code WOMPI_ALLOW_SANDBOX_IN_PROD=true} (log WARN); sin simuladores ({@code SIMULATED_PAYMENTS_ENABLED=false} y
 *       {@code APP_CHECKOUT_PAYMENT_PROVIDER} explícito y distinto de SIMULATED).</li>
 *   <li>Wompi en uso (perfil {@code prod}, PRODUCTION o proveedor WOMPI): ningún secreto vacío.</li>
 *   <li>PRODUCTION: {@code WOMPI_REDIRECT_URL}, {@code WOMPI_CHECKOUT_URL} y {@code APP_FRONTEND_BASE_URL} en
 *       {@code https://}.</li>
 * </ul>
 */
@Component
@Slf4j
public class WompiConfigurationValidator {

    /** Valores leídos de la configuración, aparte de {@code wompi.*}. */
    public record Inputs(
            boolean prodProfile,
            boolean billingSimulatedPayments,
            String checkoutPaymentProvider,
            String frontendBaseUrl,
            boolean allowSandboxInProd,
            int reservationMinutes
    ) {
        /** Sin opt-in de sandbox en prod y con la reserva por defecto (30 min). */
        public Inputs(boolean prodProfile, boolean billingSimulatedPayments, String checkoutPaymentProvider,
                String frontendBaseUrl) {
            this(prodProfile, billingSimulatedPayments, checkoutPaymentProvider, frontendBaseUrl, false,
                    DEFAULT_RESERVATION_MINUTES);
        }
    }

    static final int DEFAULT_RESERVATION_MINUTES = 30;
    static final int MIN_RESERVATION_MINUTES = 5;
    static final int MAX_RESERVATION_MINUTES = 120;

    public WompiConfigurationValidator(WompiProperties properties, Environment environment) {
        Inputs inputs = new Inputs(
                environment.acceptsProfiles(Profiles.of("prod")),
                environment.getProperty("app.billing.simulated-payments-enabled", Boolean.class, false),
                environment.getProperty("app.checkout.payment-provider", ""),
                environment.getProperty("app.frontend-base-url", ""),
                // WOMPI_ALLOW_SANDBOX_IN_PROD (enlace relajado de variables de entorno); falso por defecto.
                environment.getProperty("wompi.allow-sandbox-in-prod", Boolean.class, false),
                environment.getProperty("app.checkout.reservation-minutes", Integer.class,
                        DEFAULT_RESERVATION_MINUTES)
        );
        List<String> problems = problems(properties, inputs);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid payment configuration: " + String.join("; ", problems));
        }
        if (inputs.prodProfile() && properties.environment() != WompiEnvironment.PRODUCTION) {
            log.warn("WOMPI_ENVIRONMENT={} in the prod profile (WOMPI_ALLOW_SANDBOX_IN_PROD=true): no real charges",
                    properties.environment());
        }
    }

    public static List<String> problems(WompiProperties properties, Inputs inputs) {
        List<String> problems = new ArrayList<>();
        WompiEnvironment environment = properties.environment();
        boolean providerBlank = inputs.checkoutPaymentProvider() == null
                || inputs.checkoutPaymentProvider().isBlank();
        PaymentProvider provider = PaymentProvider.parse(inputs.checkoutPaymentProvider()).orElse(null);

        if (!providerBlank && provider == null) {
            problems.add("APP_CHECKOUT_PAYMENT_PROVIDER must be SIMULATED or WOMPI");
        }
        checkPrefix(problems, "WOMPI_PUBLIC_KEY", properties.publicKey(), environment.publicKeyPrefix(), environment);
        checkPrefix(problems, "WOMPI_PRIVATE_KEY", properties.privateKey(), environment.privateKeyPrefix(),
                environment);

        if (inputs.reservationMinutes() < MIN_RESERVATION_MINUTES
                || inputs.reservationMinutes() > MAX_RESERVATION_MINUTES) {
            problems.add("APP_CHECKOUT_RESERVATION_MINUTES must be between " + MIN_RESERVATION_MINUTES + " and "
                    + MAX_RESERVATION_MINUTES);
        }

        if (inputs.prodProfile()) {
            if (environment != WompiEnvironment.PRODUCTION && !inputs.allowSandboxInProd()) {
                problems.add("WOMPI_ENVIRONMENT must be PRODUCTION in the prod profile "
                        + "(set WOMPI_ALLOW_SANDBOX_IN_PROD=true to opt in to SANDBOX)");
            }
            if (inputs.billingSimulatedPayments()) {
                problems.add("SIMULATED_PAYMENTS_ENABLED must be false in the prod profile");
            }
            if (providerBlank) {
                problems.add("APP_CHECKOUT_PAYMENT_PROVIDER is required in the prod profile");
            } else if (provider == PaymentProvider.SIMULATED) {
                problems.add("APP_CHECKOUT_PAYMENT_PROVIDER=SIMULATED is not allowed in the prod profile");
            }
        }

        boolean wompiInUse = inputs.prodProfile()
                || environment == WompiEnvironment.PRODUCTION
                || provider == PaymentProvider.WOMPI;
        if (wompiInUse) {
            requirePresent(problems, "WOMPI_PUBLIC_KEY", properties.publicKey());
            requirePresent(problems, "WOMPI_PRIVATE_KEY", properties.privateKey());
            requirePresent(problems, "WOMPI_INTEGRITY_SECRET", properties.integritySecret());
            requirePresent(problems, "WOMPI_EVENTS_SECRET", properties.eventsSecret());
            requirePresent(problems, "WOMPI_CHECKOUT_URL", properties.checkoutUrl());
            requirePresent(problems, "WOMPI_REDIRECT_URL", properties.redirectUrl());
        }

        if (environment == WompiEnvironment.PRODUCTION) {
            requireHttps(problems, "WOMPI_REDIRECT_URL", properties.redirectUrl());
            requireHttps(problems, "WOMPI_CHECKOUT_URL", properties.checkoutUrl());
            requireHttps(problems, "APP_FRONTEND_BASE_URL", inputs.frontendBaseUrl());
        }
        return problems;
    }

    private static void checkPrefix(
            List<String> problems, String name, String value, String prefix, WompiEnvironment environment) {
        if (value != null && !value.isBlank() && !value.startsWith(prefix)) {
            problems.add(name + " must start with " + prefix + " when WOMPI_ENVIRONMENT=" + environment);
        }
    }

    private static void requirePresent(List<String> problems, String name, String value) {
        if (value == null || value.isBlank()) {
            problems.add(name + " is required when Wompi is in use");
        }
    }

    private static void requireHttps(List<String> problems, String name, String value) {
        if (value == null || !value.trim().toLowerCase(Locale.ROOT).startsWith("https://")) {
            problems.add(name + " must use https:// when WOMPI_ENVIRONMENT=PRODUCTION");
        }
    }
}
