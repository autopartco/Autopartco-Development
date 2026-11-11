package com.autopartco.backend.core.search;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.autopartco.backend.core.catalog.ProductResponse;

/**
 * Página de resultados: un listado por producto, ya hidratado con el precio del comprador. {@code fuzzy=true} si la
 * consulta exacta no dio resultados y se muestran resultados parecidos (trigramas). {@code interpreted} es nulo si no
 * se interpretó nada (o con {@code interpret=false}); {@code facets} es nulo si no se pidieron.
 */
public record SearchResult(
        List<Hit> items,
        int page,
        int size,
        long total,
        boolean fuzzy,
        SearchInterpretation interpreted,
        Facets facets
) {
    /** Listado con su distancia en km (1 decimal) cuando la consulta trae ubicación; si no, nula. */
    public record Hit(ProductResponse listing, BigDecimal distanceKm) {
    }

    public record Facets(
            List<IdCount> categories,
            List<IdCount> manufacturers,
            List<CityCount> cities,
            PriceRange price,
            long inStock,
            long onSale
    ) {
    }

    public record IdCount(UUID id, String name, long count) {
    }

    public record CityCount(String code, String name, long count) {
    }

    public record PriceRange(BigDecimal min, BigDecimal max) {
    }
}
