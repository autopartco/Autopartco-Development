package com.autopartco.backend.core.payments;

/**
 * Entorno de Wompi. Define el prefijo de las llaves, la URL base de la API y el valor de {@code environment} que
 * Wompi declara en sus eventos.
 */
public enum WompiEnvironment {
    SANDBOX("test", "https://sandbox.wompi.co/v1"),
    PRODUCTION("prod", "https://production.wompi.co/v1");

    private final String keyMarker;
    private final String apiUrl;

    WompiEnvironment(String keyMarker, String apiUrl) {
        this.keyMarker = keyMarker;
        this.apiUrl = apiUrl;
    }

    /** {@code pub_test_} o {@code pub_prod_}. */
    public String publicKeyPrefix() {
        return "pub_" + keyMarker + "_";
    }

    /** {@code prv_test_} o {@code prv_prod_}. */
    public String privateKeyPrefix() {
        return "prv_" + keyMarker + "_";
    }

    /** Valor del campo {@code environment} de los eventos de Wompi: {@code test} o {@code prod}. */
    public String eventValue() {
        return keyMarker;
    }

    public String apiUrl() {
        return apiUrl;
    }
}
