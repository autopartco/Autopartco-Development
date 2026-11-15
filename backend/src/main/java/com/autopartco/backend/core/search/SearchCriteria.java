package com.autopartco.backend.core.search;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Criterios de una búsqueda. Los filtros ({@code makeId}, {@code modelId}, {@code year}, {@code cityCode}…) son solo
 * los explícitos del cliente; lo interpretado de {@code q} va aparte en {@link Hints} y ordena (la marca interpretada
 * además descarta lo que es de otro vehículo, ver {@link SearchSql}). Texto: {@code terms} son los grupos de las
 * palabras con contenido (cada uno con sus sinónimos), que dan los candidatos y la cobertura; {@code tsQuery} (todas
 * las palabras) solo se usa si no queda ninguna palabra con contenido; {@code reference} es la referencia reconocida.
 * {@code fuzzyText} (trigramas) es excluyente con lo anterior.
 */
record SearchCriteria(
        String tsQuery,
        String reference,
        String fuzzyText,
        List<String> terms,
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
        Integer minProductRating,
        Boolean universal,
        SearchSort sort,
        Hints hints
) {
    /**
     * Palabras de la consulta que expresan la intención o el tono, no el repuesto («necesito», «dónde consigo», «me
     * hace falta», «para el carro», «original», «barato»). Siguen en el texto, pero no cuentan como palabras con
     * contenido: una consulta larga no pierde cobertura por ellas.
     */
    static final Set<String> FILLER_WORDS = Set.of("necesito", "necesita", "busco", "buscando", "quiero",
            "quisiera", "cambiar", "consigo", "conseguir", "donde", "hace", "falta", "me", "comprar", "compro",
            "venden", "vende", "tienen", "tiene", "hay", "precio", "cuanto", "cuesta", "vale", "carro", "auto",
            "vehiculo", "favor", "porfa", "urgente", "bueno", "buena", "barato", "barata", "economico", "original", "completo", "completa", "nuevo", "nueva");

    /**
     * Lo interpretado de {@code q} que el cliente no fijó. {@code makeId} (con {@code vehicleTsQuery}) solo existe sin
     * marca explícita; {@code modelId} solo si es de la marca efectiva; {@code year} (con el rango pedido
     * {@code yearFrom}–{@code yearTo}) y {@code cityCode} solo sin el parámetro explícito.
     */
    record Hints(UUID makeId, UUID modelId, Integer year, String cityCode, String vehicleTsQuery,
            String modelTsQuery, Integer yearFrom, Integer yearTo) {
        static final Hints NONE = new Hints(null, null, null, null, null, null);

        Hints {
            if (year == null) {
                yearFrom = null;
                yearTo = null;
            } else {
                yearFrom = yearFrom == null ? year : yearFrom;
                yearTo = yearTo == null ? year : yearTo;
            }
        }

        /** Año único (o ninguno): el rango es ese año. */
        Hints(UUID makeId, UUID modelId, Integer year, String cityCode, String vehicleTsQuery, String modelTsQuery) {
            this(makeId, modelId, year, cityCode, vehicleTsQuery, modelTsQuery, year, year);
        }

        boolean any() {
            return makeId != null || modelId != null || year != null || cityCode != null;
        }
    }

    SearchCriteria {
        manufacturerIds = manufacturerIds == null ? List.of() : List.copyOf(manufacturerIds);
        terms = terms == null ? List.of() : List.copyOf(terms);
        hints = hints == null ? Hints.NONE : hints;
    }

    /** Filtros explícitos de {@code query}; lo interpretado de {@code parsed} queda como pista. */
    static SearchCriteria of(SearchQuery query, ParsedQuery parsed) {
        UUID hintMake = query.makeId() == null && parsed.make() != null ? parsed.make().id() : null;
        UUID effectiveMake = query.makeId() != null ? query.makeId() : hintMake;
        UUID hintModel = query.modelId() == null && parsed.model() != null
                && parsed.model().makeId().equals(effectiveMake) ? parsed.model().id() : null;
        boolean yearHint = query.year() == null && parsed.year() != null;
        Hints hints = new Hints(
                hintMake,
                hintModel,
                yearHint ? parsed.year() : null,
                query.cityCode() == null && parsed.city() != null ? parsed.city().code() : null,
                hintMake == null ? null : parsed.vehicleTsQuery(),
                hintModel == null ? null : parsed.modelTsQuery(),
                yearHint ? parsed.yearFrom() : null,
                yearHint ? parsed.yearTo() : null);
        return new SearchCriteria(
                parsed.tsQuery(),
                parsed.reference(),
                null,
                contentTerms(parsed),
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
                query.minProductRating(),
                query.universal(),
                query.sortOrDefault(),
                hints);
    }

    /**
     * Grupos de {@code parsed.anyTerms()} (palabras sin las vacías del parser, alineadas con {@code words}) que
     * describen el repuesto: sin las palabras de relleno de {@link #FILLER_WORDS}, que no cuentan para la cobertura.
     */
    static List<String> contentTerms(ParsedQuery parsed) {
        List<String> groups = parsed.anyTerms();
        List<String> terms = new ArrayList<>();
        int group = 0;
        for (String word : parsed.words()) {
            if (QueryParser.STOPWORDS.contains(word)) {
                continue;
            }
            if (group >= groups.size()) {
                break;
            }
            String term = groups.get(group++);
            if (!FILLER_WORDS.contains(word)) {
                terms.add(term);
            }
        }
        return terms;
    }

    /** Misma búsqueda con trigramas sobre {@code text} en lugar del texto (último recurso). */
    SearchCriteria fuzzy(String text) {
        return new SearchCriteria(null, null, text, List.of(), categoryId, manufacturerIds, makeId, modelId,
                versionId, year, priceMin, priceMax, inStock, onSale, storeId, branchId, cityCode, departmentCode, lat,
                lng, radiusKm, minRating, minProductRating, universal, sort, hints);
    }

    boolean hasLocation() {
        return lat != null && lng != null;
    }

    /** Viene algún filtro de faceta (categoría, fabricante, ciudad, precio, stock o promoción). */
    boolean hasFacetFilter() {
        return categoryId != null || !manufacturerIds.isEmpty() || cityCode != null || priceMin != null
                || priceMax != null || inStock || onSale;
    }

    /** Hay palabras con contenido: candidatos acotados y cobertura. */
    boolean hasTerms() {
        return fuzzyText == null && !terms.isEmpty();
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
