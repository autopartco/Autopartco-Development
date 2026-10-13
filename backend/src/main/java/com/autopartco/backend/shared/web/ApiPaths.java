package com.autopartco.backend.shared.web;

/**
 * Prefijos de ruta de la API REST.
 */
public final class ApiPaths {

    private static final String COMMON_PATH = "/api";
    private static final String API_VERSION = "/v1";

    public static final String BASE = COMMON_PATH + API_VERSION;
    public static final String ADMIN = BASE + "/admin";
    public static final String MARKETPLACE = BASE + "/marketplace";
    public static final String STORES = BASE + "/stores";
    public static final String ME = BASE + "/me";

    private ApiPaths() {
        throw new UnsupportedOperationException("Esta clase nunca debe ser instanciada");
    }
}
