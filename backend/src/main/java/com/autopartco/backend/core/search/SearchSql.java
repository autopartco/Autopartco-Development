package com.autopartco.backend.core.search;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Construye el SQL de búsqueda sobre {@code search_listings}. Los valores van siempre como parámetros con nombre
 * ({@code :x}); el texto del SQL solo depende de qué filtros vienen, nunca de sus valores.
 *
 * <ul>
 *   <li>Texto: {@code document @@ to_tsquery('public.spanish_unaccent', :tsq)}; referencia: el mismo documento con
 *   coincidencia en peso A; respaldo: {@code :fuzzy <% search_text} (umbral de {@code word_similarity} 0,3 fijado por
 *   {@link #FUZZY_THRESHOLD_SQL} en la transacción).</li>
 *   <li>Vehículo: el índice guarda en {@code vehicle_model_ids} también la marca de las reglas sin modelo, y en
 *   {@code vehicle_version_ids} el modelo o la marca de las reglas sin versión; así «cubre» equivale a cruzar arreglos
 *   ({@code &&}), igual que {@code ListingQueries.VEHICLE_MATCH_*}. Los universales siempre coinciden.</li>
 *   <li>Cercanía: caja de coordenadas sobre {@code ix_sl_geo} y luego Haversine, como {@code BranchMapQueries}.</li>
 *   <li>Un resultado por producto: {@code distinct on (product_id)} con el listado de menor precio dentro de los
 *   filtros (con {@code sort=nearest}, el más cercano).</li>
 *   <li>Relevancia: {@code ts_rank_cd × pistas × (1 + 0,3·stock) × (1 + 0,1·calificación/5) × 1/(1 + km/25)}.</li>
 *   <li>Lo interpretado de {@code q} ({@link SearchCriteria.Hints}) no filtra. La marca interpretada solo exige
 *   {@code (sl.universal or sl.vehicle_make_ids @> [marca] or sl.document @@ (marca | modelo))}, porque la mayoría
 *   del catálogo no tiene compatibilidades cargadas. Pistas en {@link #hintBoost}: compatible con la marca o la
 *   menciona ×{@value #VEHICLE_BOOST}; con el modelo ×{@value #MODEL_BOOST}; año cubierto por
 *   {@code year_from/year_to} ×{@value #YEAR_BOOST}; misma ciudad ×{@value #CITY_BOOST}. Los parámetros explícitos
 *   siguen filtrando.</li>
 *   <li>Cualquier palabra ({@code anyTerms}): {@code document @@ (g1 | g2 | …)}; rango = palabras cubiertas +
 *   {@code ts_rank_cd / (1 + ts_rank_cd)}.</li>
 * </ul>
 */
final class SearchSql {
    static final String CONFIG = "'public.spanish_unaccent'::regconfig";
    static final String FUZZY_THRESHOLD_SQL = "select set_config('pg_trgm.word_similarity_threshold', '0.3', true)";
    static final double KM_PER_DEGREE = 111.0;
    static final int MAX_FACET_VALUES = 20;
    static final String VEHICLE_BOOST = "2.0";
    static final String MODEL_BOOST = "1.5";
    static final String YEAR_BOOST = "1.2";
    static final String CITY_BOOST = "1.2";

    /** Facetas cuyo propio filtro se omite al contarlas. */
    enum Facet { CATEGORY, MANUFACTURER, CITY, PRICE, STOCK, SALE }

    record Statement(String sql, Map<String, Object> params) {
    }

    private SearchSql() {
    }

    static final String DISTANCE_SQL = """
            2 * 6371 * asin(sqrt(least(1.0,
                power(sin(radians((sl.latitude - :lat) / 2)), 2)
                + cos(radians(:lat)) * cos(radians(sl.latitude))
                  * power(sin(radians((sl.longitude - :lng) / 2)), 2))))""";

    /** Página de claves de listado ordenadas, con distancia y total ({@code count(*) over ()}). */
    static Statement page(SearchCriteria criteria, int limit, long offset) {
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> where = conditions(criteria, params, EnumSet.noneOf(Facet.class));
        String distance = criteria.hasLocation() ? DISTANCE_SQL : "null::double precision";
        String representative = criteria.sort() == SearchSort.NEAREST
                ? "distance_km asc nulls last, price asc, listing_key"
                : "price asc, distance_km asc nulls last, listing_key";
        String decay = criteria.hasLocation() ? " * (1.0 / (1.0 + coalesce(distance_km, 0) / 25.0))" : "";
        String sql = """
                with matched as (
                    select sl.listing_key, sl.product_id, sl.branch_id, sl.name, sl.price, sl.stock, sl.store_rating,
                           sl.product_rating, sl.product_review_count, sl.published_at,
                           %s as distance_km,
                           %s as text_rank,
                           %s as hint_boost
                    from search_listings sl
                    where %s
                ), best as (
                    select distinct on (product_id) *
                    from matched
                    order by product_id, %s
                ), scored as (
                    select best.*,
                           text_rank * hint_boost * (1 + 0.3 * (case when stock > 0 then 1 else 0 end))
                               * (1 + 0.1 * coalesce(store_rating, 0) / 5)%s as score
                    from best
                )
                select listing_key, product_id, branch_id, distance_km, product_rating, product_review_count,
                       count(*) over () as total
                from scored
                order by %s
                limit :limit offset :offset
                """.formatted(distance, textRank(criteria, params), hintBoost(criteria.hints(), params),
                String.join("\n      and ", where), representative, decay, orderBy(criteria.sort()));
        params.put("limit", limit);
        params.put("offset", offset);
        return new Statement(sql, params);
    }

    /** Productos distintos que cumplen los filtros (solo si la página pedida sale vacía y hace falta el total). */
    static Statement count(SearchCriteria criteria) {
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> where = conditions(criteria, params, EnumSet.noneOf(Facet.class));
        return new Statement("select count(distinct sl.product_id) from search_listings sl where "
                + String.join("\n  and ", where), params);
    }

    /**
     * Facetas con {@code grouping sets}: cada grupo cuenta productos distintos con todos los filtros salvo el suyo
     * (las banderas {@code f_*} marcan qué filtros de faceta cumple cada fila). El grupo vacío da el rango de precios
     * y los conteos de stock y promoción.
     */
    static Statement facets(SearchCriteria criteria) {
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> where = conditions(criteria, params, EnumSet.allOf(Facet.class));
        Map<Facet, String> flags = new LinkedHashMap<>();
        for (Facet facet : Facet.values()) {
            List<String> own = facetCondition(facet, criteria, params);
            flags.put(facet, own.isEmpty() ? "true" : String.join(" and ", own));
        }
        String sql = """
                with base as (
                    select sl.product_id, sl.category_id, sl.manufacturer_id, sl.city_code, sl.price, sl.stock,
                           sl.has_promotion,
                           (%s) as f_category,
                           (%s) as f_manufacturer,
                           (%s) as f_city,
                           (%s) as f_price,
                           (%s) as f_stock,
                           (%s) as f_sale
                    from search_listings sl
                    where %s
                ), grouped as (
                    select grouping(category_id) as g_category, grouping(manufacturer_id) as g_manufacturer,
                           grouping(city_code) as g_city, category_id, manufacturer_id, city_code,
                           count(distinct product_id) filter (where %s) as n_category,
                           count(distinct product_id) filter (where %s) as n_manufacturer,
                           count(distinct product_id) filter (where %s) as n_city,
                           min(price) filter (where %s) as price_min,
                           max(price) filter (where %s) as price_max,
                           count(distinct product_id) filter (where %s and stock > 0) as n_in_stock,
                           count(distinct product_id) filter (where %s and has_promotion) as n_on_sale
                    from base
                    group by grouping sets ((category_id), (manufacturer_id), (city_code), ())
                )
                select g.*, c.code as category_name, m.name as manufacturer_name, gm.name as city_name
                from grouped g
                left join categories c on c.id = g.category_id
                left join manufacturers m on m.id = g.manufacturer_id
                left join geo_municipalities gm on gm.code = g.city_code
                """.formatted(
                flags.get(Facet.CATEGORY), flags.get(Facet.MANUFACTURER), flags.get(Facet.CITY),
                flags.get(Facet.PRICE), flags.get(Facet.STOCK), flags.get(Facet.SALE),
                String.join("\n      and ", where),
                allFlagsExcept(Facet.CATEGORY), allFlagsExcept(Facet.MANUFACTURER), allFlagsExcept(Facet.CITY),
                allFlagsExcept(Facet.PRICE), allFlagsExcept(Facet.PRICE),
                allFlagsExcept(Facet.STOCK), allFlagsExcept(Facet.SALE));
        return new Statement(sql, params);
    }

    private static String allFlagsExcept(Facet excluded) {
        List<String> flags = new ArrayList<>();
        for (Facet facet : Facet.values()) {
            if (facet != excluded) {
                flags.add("f_" + facet.name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return String.join(" and ", flags);
    }

    /** Factor de las pistas interpretadas (no filtran). Sin pistas, {@code 1.0}. */
    static String hintBoost(SearchCriteria.Hints hints, Map<String, Object> params) {
        List<String> factors = new ArrayList<>();
        if (hints.makeId() != null) {
            // Compatible por datos con la marca, o la menciona en el texto: ×2 frente a universales y demás.
            String mention = hints.vehicleTsQuery() == null ? ""
                    : " or sl.document @@ to_tsquery(" + CONFIG + ", :vehicleTsq)";
            factors.add("(case when sl.vehicle_make_ids @> array[cast(:hintMakeId as uuid)]" + mention
                    + " then " + VEHICLE_BOOST + " else 1.0 end)");
            params.put("hintMakeId", hints.makeId());
            if (hints.vehicleTsQuery() != null) {
                params.put("vehicleTsq", hints.vehicleTsQuery());
            }
        }
        if (hints.modelId() != null) {
            String mention = hints.modelTsQuery() == null ? ""
                    : " or sl.document @@ to_tsquery(" + CONFIG + ", :modelTsq)";
            factors.add("(case when sl.vehicle_model_ids @> array[cast(:hintModelId as uuid)]" + mention
                    + " then " + MODEL_BOOST + " else 1.0 end)");
            params.put("hintModelId", hints.modelId());
            if (hints.modelTsQuery() != null) {
                params.put("modelTsq", hints.modelTsQuery());
            }
        }
        if (hints.year() != null) {
            factors.add("(case when (sl.year_from is not null or sl.year_to is not null)"
                    + " and coalesce(sl.year_from, :hintYear) <= :hintYear"
                    + " and coalesce(sl.year_to, :hintYear) >= :hintYear then " + YEAR_BOOST + " else 1.0 end)");
            params.put("hintYear", hints.year());
        }
        if (hints.cityCode() != null) {
            factors.add("(case when sl.city_code = :hintCityCode then " + CITY_BOOST + " else 1.0 end)");
            params.put("hintCityCode", hints.cityCode());
        }
        return factors.isEmpty() ? "1.0" : String.join("\n                               * ", factors);
    }

    private static String textRank(SearchCriteria criteria, Map<String, Object> params) {
        if (criteria.fuzzyText() != null) {
            return "word_similarity(:fuzzy, sl.search_text)";
        }
        if (!criteria.anyTerms().isEmpty()) {
            // Palabras cubiertas (entero) + ts_rank_cd llevado a [0, 1): manda la cobertura, el rango desempata.
            List<String> covered = new ArrayList<>();
            for (int i = 0; i < criteria.anyTerms().size(); i++) {
                covered.add("(case when sl.document @@ to_tsquery(" + CONFIG + ", :any" + i + ") then 1 else 0 end)");
                params.put("any" + i, criteria.anyTerms().get(i));
            }
            String rank = "ts_rank_cd(sl.document, to_tsquery(" + CONFIG + ", :anyTsq))";
            return "(" + String.join(" + ", covered) + " + " + rank + " / (1 + " + rank + "))";
        }
        if (criteria.tsQuery() != null) {
            return "ts_rank_cd(sl.document, to_tsquery(" + CONFIG + ", :tsq))";
        }
        if (criteria.reference() != null) {
            return "ts_rank_cd(sl.document, plainto_tsquery(" + CONFIG + ", :reference))";
        }
        return "1.0";
    }

    private static String orderBy(SearchSort sort) {
        return switch (sort == null ? SearchSort.RELEVANCE : sort) {
            case RELEVANCE -> "score desc, product_id";
            case PRICE_ASC -> "price asc, product_id";
            case PRICE_DESC -> "price desc, product_id";
            case NEAREST -> "distance_km asc nulls last, product_id";
            case NEWEST -> "published_at desc, product_id";
            case RATING -> "store_rating desc nulls last, score desc, product_id";
            case PRODUCT_RATING -> "product_rating desc nulls last, product_review_count desc, score desc, product_id";
            case NAME -> "name asc, product_id";
        };
    }

    /** Condiciones del {@code WHERE} sin las de las facetas de {@code skip}. Nunca vacía. */
    static List<String> conditions(SearchCriteria c, Map<String, Object> params, Set<Facet> skip) {
        List<String> where = new ArrayList<>();
        where.add("true");
        if (c.fuzzyText() != null) {
            where.add(":fuzzy <% sl.search_text");
            params.put("fuzzy", c.fuzzyText());
        } else {
            if (c.tsQuery() != null) {
                where.add("sl.document @@ to_tsquery(" + CONFIG + ", :tsq)");
                params.put("tsq", c.tsQuery());
            }
            if (!c.anyTerms().isEmpty()) {
                where.add("sl.document @@ to_tsquery(" + CONFIG + ", :anyTsq)");
                params.put("anyTsq", String.join(" | ", c.anyTerms()));
            }
            if (c.reference() != null) {
                // Referencia, SKU o número de parte: coincidencia en el peso A (pesos {D, C, B, A}).
                where.add("sl.document @@ plainto_tsquery(" + CONFIG + ", :reference)");
                where.add("ts_rank('{0, 0, 0, 1}'::float4[], sl.document, plainto_tsquery(" + CONFIG
                        + ", :reference)) > 0");
                params.put("reference", c.reference());
            }
        }
        if (c.makeId() != null) {
            if (c.versionId() != null) {
                where.add("(sl.universal or sl.vehicle_version_ids && array[cast(:versionId as uuid), "
                        + "cast(:modelId as uuid), cast(:makeId as uuid)])");
                params.put("versionId", c.versionId());
                params.put("modelId", c.modelId());
            } else if (c.modelId() != null) {
                where.add("(sl.universal or sl.vehicle_model_ids && array[cast(:modelId as uuid), "
                        + "cast(:makeId as uuid)])");
                params.put("modelId", c.modelId());
            } else {
                where.add("(sl.universal or sl.vehicle_make_ids @> array[cast(:makeId as uuid)])");
            }
            params.put("makeId", c.makeId());
        }
        SearchCriteria.Hints hints = c.hints();
        if (hints.makeId() != null) {
            // Marca interpretada (no explícita): alternativa, no filtro. Compatible con la marca, universal o la
            // menciona en su texto ({@code marca | modelo}).
            String mention = hints.vehicleTsQuery() == null ? ""
                    : " or sl.document @@ to_tsquery(" + CONFIG + ", :vehicleTsq)";
            where.add("(sl.universal or sl.vehicle_make_ids @> array[cast(:hintMakeId as uuid)]" + mention + ")");
            params.put("hintMakeId", hints.makeId());
            if (hints.vehicleTsQuery() != null) {
                params.put("vehicleTsq", hints.vehicleTsQuery());
            }
        }
        if (c.year() != null) {
            where.add("(sl.year_from is null or sl.year_from <= :year)");
            where.add("(sl.year_to is null or sl.year_to >= :year)");
            params.put("year", c.year());
        }
        if (c.storeId() != null) {
            where.add("sl.store_id = :storeId");
            params.put("storeId", c.storeId());
        }
        if (c.branchId() != null) {
            where.add("sl.branch_id = :branchId");
            params.put("branchId", c.branchId());
        }
        if (c.departmentCode() != null) {
            where.add("sl.department_code = :departmentCode");
            params.put("departmentCode", c.departmentCode());
        }
        if (c.minRating() != null) {
            where.add("sl.store_rating >= :minRating");
            params.put("minRating", c.minRating());
        }
        if (c.minProductRating() != null) {
            where.add("sl.product_rating >= :minProductRating");
            params.put("minProductRating", c.minProductRating());
        }
        if (c.universal() != null) {
            where.add("sl.universal = :universal");
            params.put("universal", c.universal());
        }
        if (c.hasLocation()) {
            params.put("lat", c.lat());
            params.put("lng", c.lng());
            if (c.radiusKm() != null) {
                double latDelta = c.radiusKm() / KM_PER_DEGREE;
                double cos = Math.max(0.01, Math.cos(Math.toRadians(c.lat())));
                double lngDelta = c.radiusKm() / (KM_PER_DEGREE * cos);
                where.add("sl.latitude between :south and :north");
                where.add("sl.longitude between :west and :east");
                where.add(DISTANCE_SQL + " <= :radiusKm");
                params.put("south", c.lat() - latDelta);
                params.put("north", c.lat() + latDelta);
                params.put("west", c.lng() - lngDelta);
                params.put("east", c.lng() + lngDelta);
                params.put("radiusKm", c.radiusKm().doubleValue());
            }
        }
        for (Facet facet : Facet.values()) {
            if (!skip.contains(facet)) {
                where.addAll(facetCondition(facet, c, params));
            }
        }
        return where;
    }

    /** Condición propia de cada faceta (vacía si ese filtro no viene). */
    static List<String> facetCondition(Facet facet, SearchCriteria c, Map<String, Object> params) {
        List<String> own = new ArrayList<>();
        switch (facet) {
            case CATEGORY -> {
                if (c.categoryId() != null) {
                    own.add("sl.category_path @> array[cast(:categoryId as uuid)]");
                    params.put("categoryId", c.categoryId());
                }
            }
            case MANUFACTURER -> {
                if (!c.manufacturerIds().isEmpty()) {
                    own.add("sl.manufacturer_id in (:manufacturerIds)");
                    params.put("manufacturerIds", c.manufacturerIds());
                }
            }
            case CITY -> {
                if (c.cityCode() != null) {
                    own.add("sl.city_code = :cityCode");
                    params.put("cityCode", c.cityCode());
                }
            }
            case PRICE -> {
                if (c.priceMin() != null) {
                    own.add("sl.price >= :priceMin");
                    params.put("priceMin", c.priceMin());
                }
                if (c.priceMax() != null) {
                    own.add("sl.price <= :priceMax");
                    params.put("priceMax", c.priceMax());
                }
            }
            case STOCK -> {
                if (c.inStock()) {
                    own.add("sl.stock > 0");
                }
            }
            case SALE -> {
                if (c.onSale()) {
                    own.add("sl.has_promotion");
                }
            }
        }
        return own;
    }
}
