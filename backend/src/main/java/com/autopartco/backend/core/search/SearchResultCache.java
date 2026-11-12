package com.autopartco.backend.core.search;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.autopartco.backend.core.pricing.BuyerContext;
import com.autopartco.backend.shared.cache.AppCaches;
import com.autopartco.backend.shared.cache.ResilientCacheManager;
import com.autopartco.backend.shared.cache.SharedCounters;
import com.autopartco.backend.shared.cache.TypedCache;

/**
 * Caché {@code search:results} (60 s) de la parte orgánica de una búsqueda: resultados, total, {@code fuzzy},
 * interpretación y facetas. Los anuncios ({@code sponsored}) y sus tokens nunca entran: se calculan en cada llamada.
 * <p>
 * Clave: {@code v<catalogVersion>:sha256(parámetros efectivos normalizados + página + grupo de precio)}.
 * {@code catalogVersion} sube cada vez que {@link SearchIndexJobs} escribe en {@code search_listings} (cambios
 * agrupados por su ventana de espera), así que tras una reindexación las entradas viejas dejan de leerse. El grupo de
 * precio es {@code ALL} para anónimos y compradores sin segmentos, o los segmentos de plataforma ordenados; un
 * comprador con segmentos por almacén (promociones individuales) no usa la caché.
 */
@Component
public class SearchResultCache {
    static final String CATALOG_VERSION = "catalog";

    /** Parte cacheable de {@link SearchResult}. */
    record Organic(List<SearchResult.Hit> items, long total, boolean fuzzy, SearchInterpretation interpreted,
            SearchResult.Facets facets) {
    }

    /** Sin caché (pruebas). */
    static final SearchResultCache NONE = new SearchResultCache((TypedCache<Organic>) null, null);

    private final TypedCache<Organic> cache;
    private final SharedCounters versions;

    @Autowired
    public SearchResultCache(ResilientCacheManager caches, SharedCounters versions) {
        this(caches.<Organic>typed(AppCaches.SEARCH_RESULTS, Organic.class), versions);
    }

    private SearchResultCache(TypedCache<Organic> cache, SharedCounters versions) {
        this.cache = cache;
        this.versions = versions;
    }

    Organic get(String q, SearchCriteria criteria, SearchQuery query, BuyerContext buyer, Supplier<Organic> loader) {
        Optional<String> group = priceGroup(buyer);
        if (cache == null || group.isEmpty()) {
            return loader.get();
        }
        String key = "v" + versions.current(CATALOG_VERSION) + ':' + sha256(normalized(q, criteria, query) + "|g="
                + group.get());
        return cache.get(key, loader);
    }

    /** Lo llama {@link SearchIndexJobs} tras escribir en {@code search_listings}. */
    void catalogChanged() {
        if (versions != null) {
            versions.increment(CATALOG_VERSION);
        }
    }

    static Optional<String> priceGroup(BuyerContext buyer) {
        if (buyer == null || !buyer.hasSegments()) {
            return Optional.of("ALL");
        }
        if (!buyer.storeSegments().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("P:" + String.join(",", new TreeSet<>(buyer.platformSegments())));
    }

    /** Filtros explícitos y pistas interpretadas (por separado) con orden y formato estables. */
    static String normalized(String q, SearchCriteria c, SearchQuery query) {
        StringBuilder key = new StringBuilder(256);
        add(key, "q", q == null ? null : q.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "));
        add(key, "ts", c.tsQuery());
        add(key, "ref", c.reference());
        add(key, "cat", c.categoryId());
        add(key, "man", c.manufacturerIds().isEmpty() ? null
                : new TreeSet<>(c.manufacturerIds().stream().map(UUID::toString).toList()));
        add(key, "make", c.makeId());
        add(key, "model", c.modelId());
        add(key, "ver", c.versionId());
        add(key, "year", c.year());
        add(key, "pmin", plain(c.priceMin()));
        add(key, "pmax", plain(c.priceMax()));
        add(key, "stock", c.inStock());
        add(key, "sale", c.onSale());
        add(key, "store", c.storeId());
        add(key, "branch", c.branchId());
        add(key, "city", c.cityCode());
        add(key, "dep", c.departmentCode());
        add(key, "lat", c.lat());
        add(key, "lng", c.lng());
        add(key, "r", c.radiusKm());
        add(key, "rating", c.minRating());
        add(key, "prating", c.minProductRating());
        add(key, "uni", c.universal());
        add(key, "sort", c.sort());
        // Pistas interpretadas (ordenan, no filtran), separadas de los filtros explícitos de arriba.
        SearchCriteria.Hints hints = c.hints();
        add(key, "hmake", hints.makeId());
        add(key, "hmodel", hints.modelId());
        add(key, "hyear", hints.year());
        add(key, "hcity", hints.cityCode());
        add(key, "hvts", hints.vehicleTsQuery());
        add(key, "hmts", hints.modelTsQuery());
        add(key, "page", query.pageOrDefault());
        add(key, "size", query.sizeOrDefault());
        add(key, "facets", query.facetsOrDefault());
        add(key, "interpret", query.interpretOrDefault());
        return key.toString();
    }

    private static void add(StringBuilder key, String name, Object value) {
        key.append(name).append('=').append(value == null ? "" : value).append('&');
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
