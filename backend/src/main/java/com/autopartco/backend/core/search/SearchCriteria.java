package com.autopartco.backend.core.search;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Criterios de una búsqueda. Los filtros ({@code makeId}, {@code modelId}, {@code year}, {@code cityCode}…) son solo
 * los explícitos del cliente; lo interpretado de {@code q} va aparte en {@link Hints} y solo ordena (salvo la
 * alternativa de vehículo, ver {@link SearchSql}). Modos de texto excluyentes: {@code tsQuery}/{@code reference}
 * (todas las palabras), {@code anyTerms} (cualquier palabra) y {@code fuzzyText} (trigramas).
 */
record SearchCriteria(
        String tsQuery,
        String reference,
        String fuzzyText,
        List<String> anyTerms,
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
        SearchSort sort,
        Hints hints
) {
    /**
     * Lo interpretado de {@code q} que el cliente no fijó. {@code makeId} (con {@code vehicleTsQuery}) solo existe sin
     * marca explícita; {@code modelId} solo si es de la marca efectiva; {@code year} y {@code cityCode} solo sin el
     * parámetro explícito.
     */
    record Hints(UUID makeId, UUID modelId, Integer year, String cityCode, String vehicleTsQuery,
            String modelTsQuery) {
        static final Hints NONE = new Hints(null, null, null, null, null, null);

        boolean any() {
            return makeId != null || modelId != null || year != null || cityCode != null;
        }
    }

    SearchCriteria {
        manufacturerIds = manufacturerIds == null ? List.of() : List.copyOf(manufacturerIds);
        anyTerms = anyTerms == null ? List.of() : List.copyOf(anyTerms);
        hints = hints == null ? Hints.NONE : hints;
    }

    /** Filtros explícitos de {@code query}; lo interpretado de {@code parsed} queda como pista. */
    static SearchCriteria of(SearchQuery query, ParsedQuery parsed) {
        UUID hintMake = query.makeId() == null && parsed.make() != null ? parsed.make().id() : null;
        UUID effectiveMake = query.makeId() != null ? query.makeId() : hintMake;
        UUID hintModel = query.modelId() == null && parsed.model() != null
                && parsed.model().makeId().equals(effectiveMake) ? parsed.model().id() : null;
        Hints hints = new Hints(
                hintMake,
                hintModel,
                query.year() == null ? parsed.year() : null,
                query.cityCode() == null && parsed.city() != null ? parsed.city().code() : null,
                hintMake == null ? null : parsed.vehicleTsQuery(),
                hintModel == null ? null : parsed.modelTsQuery());
        return new SearchCriteria(
                parsed.tsQuery(),
                parsed.reference(),
                null,
                List.of(),
                query.categoryId(),
                query.manufacturerIds(),
                query.makeId(),
                query.modelId(),
                query.versionId(),
                query.year(),
                query.priceMin(),
                query.priceMax(),
                Boolean.TRUE.equals(query.inStock()),
                Boolean.TRUE.equals(query.onSale()),
                query.storeId(),
                query.branchId(),
                query.cityCode(),
                query.departmentCode(),
                query.lat() == null ? null : query.lat().doubleValue(),
                query.lng() == null ? null : query.lng().doubleValue(),
                query.radiusKm(),
                query.minRating(),
                query.universal(),
                query.sortOrDefault(),
                hints);
    }

    /** Misma búsqueda con trigramas sobre {@code text} en lugar del texto exacto. */
    SearchCriteria fuzzy(String text) {
        return new SearchCriteria(null, null, text, List.of(), categoryId, manufacturerIds, makeId, modelId,
                versionId, year, priceMin, priceMax, inStock, onSale, storeId, branchId, cityCode, departmentCode, lat,
                lng, radiusKm, minRating, universal, sort, hints);
    }

    /** Misma búsqueda con cualquiera de los grupos {@code terms} (OR) en lugar de todos; conserva la referencia. */
    SearchCriteria anyWord(List<String> terms) {
        return new SearchCriteria(null, reference, null, terms, categoryId, manufacturerIds, makeId, modelId,
                versionId, year, priceMin, priceMax, inStock, onSale, storeId, branchId, cityCode, departmentCode, lat,
                lng, radiusKm, minRating, universal, sort, hints);
    }

    boolean hasLocation() {
        return lat != null && lng != null;
    }

    /** Marca para la segmentación de anuncios: la explícita o la interpretada. */
    UUID targetMakeId() {
        return makeId != null ? makeId : hints.makeId();
    }

    UUID targetModelId() {
        return modelId != null ? modelId : hints.modelId();
    }

    String targetCityCode() {
        return cityCode != null ? cityCode : hints.cityCode();
    }
}
