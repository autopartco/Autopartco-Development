package com.autopartco.backend.core.payments;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de Wompi ({@code wompi.*}). La valida al arrancar {@code WompiConfigurationValidator}. Los secretos
 * nunca salen en {@link #toString()}.
 *
 * @param environment     SANDBOX (por defecto) o PRODUCTION
 * @param publicKey       {@code pub_test_…} / {@code pub_prod_…}
 * @param privateKey      {@code prv_test_…} / {@code prv_prod_…}; solo backend, para consultar transacciones
 * @param integritySecret secreto de la firma de integridad del Web Checkout
 * @param eventsSecret    secreto de la firma de eventos (webhook)
 * @param checkoutUrl     URL del Web Checkout
 * @param redirectUrl     URL de regreso del portal (pagos de suscripción)
 */
@ConfigurationProperties(prefix = "wompi")
public record WompiProperties(
        WompiEnvironment environment,
        String publicKey,
        String privateKey,
        String integritySecret,
        String eventsSecret,
        String checkoutUrl,
        String redirectUrl
) {
    public WompiProperties {
        if (environment == null) {
            environment = WompiEnvironment.SANDBOX;
        }
    }

    /** URL base de la API, derivada del entorno. */
    public String apiUrl() {
        return environment.apiUrl();
    }

    /** Llave pública, integridad, checkout y redirect configurados: el Web Checkout se puede firmar. */
    public boolean checkoutConfigured() {
        return !isBlank(publicKey) && !isBlank(integritySecret) && !isBlank(checkoutUrl) && !isBlank(redirectUrl);
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public String toString() {
        return "WompiProperties[environment=" + environment
                + ", publicKey=" + publicKey
                + ", privateKey=" + mask(privateKey)
                + ", integritySecret=" + mask(integritySecret)
                + ", eventsSecret=" + mask(eventsSecret)
                + ", checkoutUrl=" + checkoutUrl
                + ", redirectUrl=" + redirectUrl + "]";
    }

    private static String mask(String secret) {
        return isBlank(secret) ? "<vacío>" : "****";
    }
}
