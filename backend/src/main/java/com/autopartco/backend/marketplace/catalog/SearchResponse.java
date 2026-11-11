package com.autopartco.backend.marketplace.catalog;

import java.math.BigDecimal;
import java.util.List;

import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.search.SearchInterpretation;
import com.autopartco.backend.core.search.SearchResult;
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
        List<Item> sponsored
) {
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
