package com.autopartco.backend.core.search;

import java.util.List;

import com.autopartco.backend.core.organization.ServiceCategory;

/**
 * Página de servicios: uno por servicio. {@code facets} es nulo si no se pidieron. {@code page} desde 0.
 */
public record ServiceSearchResult(List<ServiceHit> items, int page, int size, long total, Facets facets) {
    public ServiceSearchResult {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static ServiceSearchResult empty(int page, int size) {
        return new ServiceSearchResult(List.of(), page, size, 0, null);
    }

    /** Categorías con su número de servicios y ciudades con código DIVIPOLA, nombre y número. */
    public record Facets(List<CategoryCount> categories, List<SearchResult.CityCount> cities) {
    }

    public record CategoryCount(ServiceCategory category, long count) {
    }
}
