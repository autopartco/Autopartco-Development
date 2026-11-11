package com.autopartco.backend.core.search;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Filtros efectivos de una búsqueda: los explícitos del cliente más los interpretados de {@code q} que el cliente no
 * fijó. {@code tsQuery}/{@code reference} (texto exacto) y {@code fuzzyText} (trigramas) son excluyentes.
 */
record SearchCriteria(
        String tsQuery,
        String reference,
        String fuzzyText,
        UUID categoryId,
        List<UUID> manufacturerIds,
        UUID makeId,
        UUID modelId,
        UUID versionId,
        Integer year,
        BigDecimal priceMin,
        BigDecimal priceMax,
        boolean inStock,
        boolean onSale,
        UUID storeId,
        UUID branchId,
        String cityCode,
        String departmentCode,
        Double lat,
        Double lng,
        Integer radiusKm,
        Integer minRating,
        Boolean universal,
        SearchSort sort
) {
    SearchCriteria {
        manufacturerIds = manufacturerIds == null ? List.of() : List.copyOf(manufacturerIds);
    }

    /**
     * Combina la consulta con lo interpretado: un filtro explícito gana. El modelo interpretado solo se usa si la marca
     * efectiva es la suya.
     */
    static SearchCriteria of(SearchQuery query, ParsedQuery parsed) {
        UUID makeId = query.makeId() != null ? query.makeId() : parsed.make() == null ? null : parsed.make().id();
        UUID modelId = query.modelId();
        if (modelId == null && parsed.model() != null && parsed.model().makeId().equals(makeId)) {
            modelId = parsed.model().id();
        }
        return new SearchCriteria(
                parsed.tsQuery(),
                parsed.reference(),
                null,
                query.categoryId(),
                query.manufacturerIds(),
                makeId,
                modelId,
                query.versionId(),
                query.year() != null ? query.year() : parsed.year(),
                query.priceMin(),
                query.priceMax(),
                Boolean.TRUE.equals(query.inStock()),
                Boolean.TRUE.equals(query.onSale()),
                query.storeId(),
                query.branchId(),
                query.cityCode() != null ? query.cityCode() : parsed.city() == null ? null : parsed.city().code(),
                query.departmentCode(),
                query.lat() == null ? null : query.lat().doubleValue(),
                query.lng() == null ? null : query.lng().doubleValue(),
                query.radiusKm(),
                query.minRating(),
                query.universal(),
                query.sortOrDefault());
    }

    /** Misma búsqueda con trigramas sobre {@code text} en lugar del texto exacto. */
    SearchCriteria fuzzy(String text) {
        return new SearchCriteria(null, null, text, categoryId, manufacturerIds, makeId, modelId, versionId, year,
                priceMin, priceMax, inStock, onSale, storeId, branchId, cityCode, departmentCode, lat, lng, radiusKm,
                minRating, universal, sort);
    }

    boolean hasLocation() {
        return lat != null && lng != null;
    }
}
