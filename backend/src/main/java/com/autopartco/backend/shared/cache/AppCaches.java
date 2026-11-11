package com.autopartco.backend.shared.cache;

import java.time.Duration;
import java.util.List;

/**
 * Cachés de la tabla de §4 del plan v2.0.0. Los anuncios ya elegidos y sus tokens nunca se cachean: solo los candidatos
 * por ubicación ({@link #ADS_ACTIVE}); el tope de frecuencia ({@code ads:freq}) es un contador aparte.
 */
public final class AppCaches {
    public static final String CATALOG_OPTIONS = "catalog:options";
    public static final String SEARCH_RESULTS = "search:results";
    public static final String SEARCH_SUGGEST = "search:suggest";
    public static final String STORE_PROFILE = "store:profile";
    public static final String STORE_BRANCHES = "store:branches";
    public static final String SETTINGS_STORE = "settings:store";
    public static final String HOME = "home";
    public static final String ADS_ACTIVE = "ads:active";

    /** Nombre, caducidad y tope de entradas de la copia local (Caffeine). */
    public record Spec(String name, Duration ttl, long maxLocalEntries) {
    }

    public static final List<Spec> SPECS = List.of(
            new Spec(CATALOG_OPTIONS, Duration.ofHours(1), 50),
            new Spec(SEARCH_RESULTS, Duration.ofSeconds(60), 2_000),
            new Spec(SEARCH_SUGGEST, Duration.ofMinutes(5), 2_000),
            new Spec(STORE_PROFILE, Duration.ofMinutes(5), 1_000),
            new Spec(STORE_BRANCHES, Duration.ofMinutes(5), 1_000),
            new Spec(SETTINGS_STORE, Duration.ofMinutes(5), 2_000),
            new Spec(HOME, Duration.ofSeconds(60), 500),
            new Spec(ADS_ACTIVE, Duration.ofSeconds(60), 20));

    private AppCaches() {
    }
}
