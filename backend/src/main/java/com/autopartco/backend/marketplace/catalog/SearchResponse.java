package com.autopartco.backend.marketplace.catalog;

import java.math.BigDecimal;
import java.util.List;

import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.search.SearchInterpretation;
import com.autopartco.backend.core.search.SearchResult;
import com.autopartco.backend.core.search.StoreSearchItem;
import com.autopartco.backend.marketplace.services.ServiceCard;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * Respuesta de {@code GET /api/v1/marketplace/search} (§1 del plan v2.0.0). {@code page} desde 0. {@code sponsored}:
 * hasta 2 anuncios {@code SEARCH_TOP} con {@code sponsored: true} y {@code adImpressionToken} (vacío sin campañas que
 * coincidan); el frontend los muestra con la etiqueta «Patrocinado».
 */
public record SearchResponse(
        List<Item> items,
        int page,
        int size,
        long total,
        boolean fuzzy,
        SearchInterpretation interpreted,
        SearchResult.Facets facets,
        List<Item> sponsored,
        @JsonInclude(JsonInclude.Include.NON_NULL) Services services,
        @JsonInclude(JsonInclude.Include.NON_NULL) String suggestedTab,
        @JsonInclude(JsonInclude.Include.NON_NULL) Stores stores
) {
    /** Franja de servicios de la búsqueda unificada: hasta 4 tarjetas y el total de servicios que coinciden. */
    public record Services(List<ServiceCard> items, long total) {
    }

    /** Franja de almacenes de la búsqueda unificada: hasta 4 y el total de almacenes que coinciden. */
    public record Stores(List<StoreSearchItem> items, long total) {
    }

    /** Sin servicios (compatibilidad con quien construye la respuesta de productos). */
    public SearchResponse(List<Item> items, int page, int size, long total, boolean fuzzy,
            SearchInterpretation interpreted, SearchResult.Facets facets, List<Item> sponsored) {
        this(items, page, size, total, fuzzy, interpreted, facets, sponsored, null, null, null);
    }

    /** Misma respuesta con la franja de servicios y la pestaña sugerida ({@code services}, o {@code null}). */
    SearchResponse withServices(Services services, String suggestedTab) {
        return new SearchResponse(items, page, size, total, fuzzy, interpreted, facets, sponsored, services,
                suggestedTab, stores);
    }

    /** Misma respuesta con la franja de almacenes ({@code null} la omite). */
    SearchResponse withStores(Stores stores) {
        return new SearchResponse(items, page, size, total, fuzzy, interpreted, facets, sponsored, services,
                suggestedTab, stores);
    }

    /** {@link ProductResponse} con sus campos al mismo nivel, más distancia y datos de anuncio. */
    public record Item(
            @JsonUnwrapped ProductResponse listing,
            BigDecimal distanceKm,
            boolean sponsored,
            String adImpressionToken
    ) {
        static Item organic(SearchResult.Hit hit) {
            return new Item(hit.listing(), hit.distanceKm(), false, null);
        }

        static Item sponsored(SearchResult.Sponsored ad) {
            return new Item(ad.hit().listing(), ad.hit().distanceKm(), true, ad.adImpressionToken());
        }
    }

    static SearchResponse of(SearchResult result) {
        return new SearchResponse(result.items().stream().map(Item::organic).toList(), result.page(), result.size(),
                result.total(), result.fuzzy(), result.interpreted(), result.facets(),
                result.sponsored().stream().map(Item::sponsored).toList());
    }
}
