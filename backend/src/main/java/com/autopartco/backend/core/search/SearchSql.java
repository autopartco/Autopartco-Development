package com.autopartco.backend.core.search;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Construye el SQL de búsqueda sobre {@code search_listings}. Los valores van siempre como parámetros con nombre
 * ({@code :x}); el texto del SQL solo depende de qué filtros vienen, nunca de sus valores.
 *
 * <p>Una sola sentencia por página (sin filtros de faceta, con las facetas en la misma sentencia:
 * {@link #pageWithFacets}), en tres pasos ({@link #source}):</p>
 * <ol>
 *   <li><b>Candidatos</b> ({@code cand}): {@code document @@ (g1 | g2 | … | referencia)} sobre el GIN de
 *   {@code document}, con los filtros explícitos y la exclusión del vehículo interpretado; los
 *   {@value #CANDIDATES} mejores por {@code ts_rank_cd}. Sin palabras con contenido (solo referencia, solo palabras
 *   vacías o sin texto) no hay tope y el texto filtra como siempre: referencia en peso A, todas las palabras.</li>
 *   <li><b>Puntuación</b> ({@code scored}), solo sobre esos candidatos: palabras cubiertas (un
 *   {@code case when document @@ :tN} por grupo, máximo {@value #MAX_TERMS}; la referencia cuenta como una más),
 *   referencia exacta (peso A, cuenta como cobertura completa), nivel de vehículo, año y ciudad.</li>
 *   <li><b>Cobertura</b> ({@code kept}): con 1–2 unidades se exigen todas; con más, al menos el 70 %. Tramos 3 (todas),
 *   2 (≥ 85 %) y 1 (≥ 70 %).</li>
 * </ol>
 * <p>Nivel de vehículo interpretado ({@link SearchCriteria.Hints}, nunca los filtros explícitos): 4 compatible por
 * datos con el modelo; 3 compatible con la marca (sin modelo interpretado, o regla de toda la marca) o menciona el
 * modelo; 2 sin datos de compatibilidad y menciona la marca; 1 universal, o sin datos ni mención de vehículo; 0 de
 * otro vehículo (sus datos son de otro modelo o marca, aunque mencione la marca) o solo menciona otras marcas. Con
 * marca interpretada el nivel 0 se descarta ya en los candidatos. Las menciones son
 * {@code mentioned_make_ids}/{@code mentioned_model_ids} del índice: cruces de arreglos, sin texto por fila.</p>
 * <p>Relevancia, lexicográfica: tramo de cobertura, referencia exacta, nivel de vehículo, año pedido cubierto por
 * {@code year_from/year_to}, categoría aprendida de los clics ({@link QueryLearning}; solo ordena), misma ciudad
 * interpretada, {@code ts_rank_cd}, stock, calificación de la tienda y
 * cercanía ({@code 1/(1 + km/25)}). Los demás órdenes usan el mismo conjunto y desempatan por relevancia.</p>
 * <ul>
 *   <li>Vehículo explícito: el índice guarda en {@code vehicle_model_ids} también la marca de las reglas sin modelo, y
 *   en {@code vehicle_version_ids} el modelo o la marca de las reglas sin versión; así «cubre» equivale a cruzar
 *   arreglos ({@code &&}), igual que {@code ListingQueries.VEHICLE_MATCH_*}. Los universales siempre coinciden.</li>
 *   <li>Cercanía: caja de coordenadas sobre {@code ix_sl_geo} y luego Haversine, como {@code BranchMapQueries}.</li>
 *   <li>Un resultado por producto: {@code distinct on (product_id)} con el listado de menor precio dentro de los
 *   filtros (con {@code sort=nearest}, el más cercano).</li>
 *   <li>Trigramas ({@code fuzzyText}): {@code :fuzzy <% search_text} (umbral de {@code word_similarity} 0,3 fijado por
 *   {@link #FUZZY_THRESHOLD_SQL} en la transacción).</li>
 * </ul>
 */
final class SearchSql {
    static final String CONFIG = "'public.spanish_unaccent'::regconfig";
    static final String FUZZY_THRESHOLD_SQL = "select set_config('pg_trgm.word_similarity_threshold', '0.3', true)";
    static final double KM_PER_DEGREE = 111.0;
    static final int MAX_FACET_VALUES = 20;
    /** Tope de listados candidatos que se puntúan cuando hay palabras con contenido. */
    static final int CANDIDATES = 500;
    /** Grupos de palabras que cuentan para la cobertura (el parser ya corta en 12 palabras). */
    static final int MAX_TERMS = 12;
    /** Rango de la referencia solo en el peso A (pesos {D, C, B, A}): nombre, número de parte o SKU. */
    static final String REFERENCE_WEIGHT_A = "ts_rank('{0, 0, 0, 1}'::float4[], sl.document, "
            + "(select plainto_tsquery(" + CONFIG + ", :reference)))";

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

    /**
     * Página de claves de listado ordenadas, con distancia, total ({@code count(*) over ()}) y el mejor nivel de
     * vehículo del conjunto ({@code best_level}, para {@code relaxed}).
     */
    static Statement page(SearchCriteria criteria, int limit, long offset) {
        Map<String, Object> params = new LinkedHashMap<>();
        String source = source(criteria, params, EnumSet.noneOf(Facet.class), "");
        String representative = representative(criteria);
        String sql = source + """
                , best as (
                    select distinct on (product_id) *
                    from kept
                    order by product_id, %s
                )
                select listing_key, product_id, branch_id, distance_km, product_rating, product_review_count,
                       count(*) over () as total, max(vehicle_level) over () as best_level
                from best
                order by %s
                limit :limit offset :offset
                """.formatted(representative, orderBy(criteria));
        params.put("limit", limit);
        params.put("offset", offset);
        return new Statement(sql, params);
    }

    /**
     * Página y facetas en una sola sentencia, cuando no hay filtros de faceta (sin ellos, cada faceta cuenta sobre el
     * mismo conjunto {@code kept} que la página, que se calcula una vez). Filas {@code part = 0}: la página, en orden
     * ({@code ord}); {@code part = 1}: los grupos de facetas, con las mismas columnas que {@link #facets}.
     */
    static Statement pageWithFacets(SearchCriteria criteria, int limit, long offset) {
        if (criteria.hasFacetFilter()) {
            throw new IllegalArgumentException("Con filtros de faceta, las facetas van en su propia sentencia");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        String source = source(criteria, params, EnumSet.noneOf(Facet.class), "");
        String sql = source + """
                , best as (
                    select distinct on (product_id) *
                    from kept
                    order by product_id, %s
                ), ranked as (
                    select best.*, count(*) over () as total, max(vehicle_level) over () as best_level,
                           row_number() over (order by %s) as ord
                    from best
                ), grouped as (
                    select grouping(category_id) as g_category, grouping(manufacturer_id) as g_manufacturer,
                           grouping(city_code) as g_city, category_id, manufacturer_id, city_code,
                           count(distinct product_id) as n, min(price) as price_min, max(price) as price_max,
                           count(distinct product_id) filter (where stock > 0) as n_in_stock,
                           count(distinct product_id) filter (where has_promotion) as n_on_sale
                    from kept
                    group by grouping sets ((category_id), (manufacturer_id), (city_code), ())
                )
                select 0 as part, ord, listing_key, product_id, branch_id, distance_km, product_rating,
                       product_review_count, total, best_level,
                       null::int as g_category, null::int as g_manufacturer, null::int as g_city,
                       null::uuid as category_id, null::uuid as manufacturer_id, null::text as city_code,
                       null::bigint as n_category, null::bigint as n_manufacturer, null::bigint as n_city,
                       null::numeric as price_min, null::numeric as price_max,
                       null::bigint as n_in_stock, null::bigint as n_on_sale,
                       null::text as category_name, null::text as manufacturer_name, null::text as city_name
                from ranked
                where ord > :offset and ord <= :offset + :limit
                union all
                select 1, null, null, null, null, null, null, null, null, null,
                       g.g_category, g.g_manufacturer, g.g_city, g.category_id, g.manufacturer_id, g.city_code::text,
                       g.n, g.n, g.n, g.price_min, g.price_max, g.n_in_stock, g.n_on_sale,
                       c.code, m.name, gm.name
                from grouped g
                left join categories c on c.id = g.category_id
                left join manufacturers m on m.id = g.manufacturer_id
                left join geo_municipalities gm on gm.code = g.city_code
                order by part, ord
                """.formatted(representative(criteria), orderBy(criteria));
        params.put("limit", limit);
        params.put("offset", offset);
        return new Statement(sql, params);
    }

    private static String representative(SearchCriteria criteria) {
        return criteria.sort() == SearchSort.NEAREST
                ? "distance_km asc nulls last, price asc, listing_key"
                : "price asc, distance_km asc nulls last, listing_key";
    }

    /** Productos distintos que cumplen los filtros (solo si la página pedida sale vacía y hace falta el total). */
    static Statement count(SearchCriteria criteria) {
        Map<String, Object> params = new LinkedHashMap<>();
        String source = source(criteria, params, EnumSet.noneOf(Facet.class), "");
        return new Statement(source + "\nselect count(distinct product_id) from kept", params);
    }

    /**
     * Facetas con {@code grouping sets} sobre el mismo conjunto que la página (candidatos, cobertura y exclusión de
     * vehículo): cada grupo cuenta productos distintos con todos los filtros salvo el suyo (las banderas {@code f_*}
     * marcan qué filtros de faceta cumple cada fila). El grupo vacío da el rango de precios y los conteos de stock y
     * promoción. Sin filtros de faceta activos, los conteos salen del mismo conjunto que {@code total}.
     */
    static Statement facets(SearchCriteria criteria) {
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> flags = new ArrayList<>();
        for (Facet facet : Facet.values()) {
            List<String> own = facetCondition(facet, criteria, params);
            flags.add("(" + (own.isEmpty() ? "true" : String.join(" and ", own)) + ") as " + flag(facet));
        }
        String source = source(criteria, params, EnumSet.allOf(Facet.class),
                ",\n           " + String.join(",\n           ", flags));
        String sql = source + """
                , grouped as (
                    select grouping(category_id) as g_category, grouping(manufacturer_id) as g_manufacturer,
                           grouping(city_code) as g_city, category_id, manufacturer_id, city_code,
                           count(distinct product_id) filter (where %s) as n_category,
                           count(distinct product_id) filter (where %s) as n_manufacturer,
                           count(distinct product_id) filter (where %s) as n_city,
                           min(price) filter (where %s) as price_min,
                           max(price) filter (where %s) as price_max,
                           count(distinct product_id) filter (where %s and stock > 0) as n_in_stock,
                           count(distinct product_id) filter (where %s and has_promotion) as n_on_sale
                    from kept
                    group by grouping sets ((category_id), (manufacturer_id), (city_code), ())
                )
                select g.*, c.code as category_name, m.name as manufacturer_name, gm.name as city_name
                from grouped g
                left join categories c on c.id = g.category_id
                left join manufacturers m on m.id = g.manufacturer_id
                left join geo_municipalities gm on gm.code = g.city_code
                """.formatted(
                allFlagsExcept(Facet.CATEGORY), allFlagsExcept(Facet.MANUFACTURER), allFlagsExcept(Facet.CITY),
                allFlagsExcept(Facet.PRICE), allFlagsExcept(Facet.PRICE),
                allFlagsExcept(Facet.STOCK), allFlagsExcept(Facet.SALE));
        return new Statement(sql, params);
    }

    private static String flag(Facet facet) {
        return "f_" + facet.name().toLowerCase(Locale.ROOT);
    }

    private static String allFlagsExcept(Facet excluded) {
        List<String> flags = new ArrayList<>();
        for (Facet facet : Facet.values()) {
            if (facet != excluded) {
                flags.add(flag(facet));
            }
        }
        return String.join(" and ", flags);
    }

    /**
     * {@code with cand as (…), scored as (…), kept as (…)}: candidatos (con {@code extraColumns}), su puntuación y
     * el filtro de cobertura. {@code kept} lleva las columnas del listado más {@code text_rank}, {@code distance_km},
     * {@code covered}, {@code ref_exact}, {@code vehicle_level}, {@code year_ok}, {@code city_ok} y {@code cov_tier}.
     */
    static String source(SearchCriteria c, Map<String, Object> params, Set<Facet> skip, String extraColumns) {
        List<String> where = conditions(c, params, skip);
        String rank = rank(c, candidateQuery(c, params));
        String limit = c.hasTerms() ? "\n    order by text_rank desc, sl.listing_key\n    limit " + CANDIDATES : "";
        String distance = c.hasLocation() ? DISTANCE_SQL : "null::double precision";
        Coverage coverage = coverage(c, params);
        return """
                with cand as (
                    select sl.listing_key, sl.product_id, sl.branch_id, sl.name, sl.price, sl.stock, sl.store_rating,
                           sl.product_rating, sl.product_review_count, sl.published_at, sl.category_id,
                           sl.manufacturer_id, sl.city_code, sl.has_promotion, sl.universal, sl.document,
                           sl.latitude, sl.longitude, sl.year_from, sl.year_to, sl.vehicle_make_ids,
                           sl.vehicle_model_ids, sl.mentioned_make_ids, sl.mentioned_model_ids,
                           %s as text_rank%s%s
                    from search_listings sl
                    where %s%s
                ), scored as (
                    select sl.*,
                           %s as distance_km,
                           %s as covered,
                           %s as ref_exact,
                           %s as vehicle_level,
                           %s as year_ok,
                           %s as city_ok
                    from cand sl
                ), kept as (
                    select scored.*, %s as cov_tier
                    from scored
                    where %s
                )""".formatted(rank, extraColumns, learnedColumn(c.hints(), params), String.join("\n      and ", where), limit, distance,
                coverage.covered(), coverage.referenceExact(), vehicleLevel(c.hints(), params),
                yearCovered(c.hints(), params), cityMatch(c.hints(), params), coverage.tier(), coverage.filter());
    }

    /**
     * {@code cat_ok}: 1 si el producto está en la categoría que los compradores eligen para esta consulta
     * ({@link QueryLearning}), vía {@code category_path @> [categoría]}. Sin categoría aprendida no añade columna.
     */
    private static String learnedColumn(SearchCriteria.Hints hints, Map<String, Object> params) {
        if (hints.learnedCategoryId() == null) {
            return "";
        }
        params.put("learnedCategoryId", hints.learnedCategoryId());
        return ",\n                           (case when sl.category_path @> array[cast(:learnedCategoryId as uuid)]"
                + " then 1 else 0 end) as cat_ok";
    }

    /** {@code tsquery} de los candidatos: grupos con contenido unidos por {@code |}, más la referencia. */
    private static String candidateQuery(SearchCriteria c, Map<String, Object> params) {
        if (!c.hasTerms()) {
            return null;
        }
        params.put("candTsq", String.join(" | ", terms(c)));
        if (c.reference() == null) {
            return tsquery("candTsq");
        }
        params.put("reference", c.reference());
        return "(select to_tsquery(" + CONFIG + ", :candTsq) || plainto_tsquery(" + CONFIG + ", :reference))";
    }

    /**
     * {@code (select to_tsquery(cfg, :param))}: una subconsulta escalar (InitPlan) se evalúa una vez por ejecución
     * también con planes genéricos de sentencias preparadas, y sirve de clave del GIN.
     */
    static String tsquery(String param) {
        return "(select to_tsquery(" + CONFIG + ", :" + param + "))";
    }

    static String plainTsquery(String param) {
        return "(select plainto_tsquery(" + CONFIG + ", :" + param + "))";
    }

    private static List<String> terms(SearchCriteria c) {
        return c.terms().size() > MAX_TERMS ? c.terms().subList(0, MAX_TERMS) : c.terms();
    }

    private static String rank(SearchCriteria criteria, String candidateQuery) {
        if (criteria.fuzzyText() != null) {
            return "word_similarity(:fuzzy, sl.search_text)";
        }
        if (candidateQuery != null) {
            return "ts_rank_cd(sl.document, " + candidateQuery + ")";
        }
        if (criteria.tsQuery() != null) {
            return "ts_rank_cd(sl.document, " + tsquery("tsq") + ")";
        }
        if (criteria.reference() != null) {
            return "ts_rank_cd(sl.document, " + plainTsquery("reference") + ")";
        }
        return "1.0";
    }

    /** Expresiones de cobertura sobre {@code scored}/{@code kept}. */
    private record Coverage(String covered, String referenceExact, String tier, String filter) {
    }

    private static Coverage coverage(SearchCriteria c, Map<String, Object> params) {
        String exact = c.reference() == null || c.fuzzyText() != null ? "false"
                : "(sl.document @@ " + plainTsquery("reference") + " and " + REFERENCE_WEIGHT_A + " > 0)";
        if (!c.hasTerms()) {
            return new Coverage("0", exact, "3", "true");
        }
        List<String> covered = new ArrayList<>();
        List<String> groups = terms(c);
        for (int i = 0; i < groups.size(); i++) {
            covered.add("(case when sl.document @@ " + tsquery("t" + i) + " then 1 else 0 end)");
            params.put("t" + i, groups.get(i));
        }
        if (c.reference() != null) {
            covered.add("(case when sl.document @@ " + plainTsquery("reference") + " then 1 else 0 end)");
        }
        int units = covered.size();
        params.put("covFull", units);
        params.put("covHigh", units <= 2 ? units : minimumCovered(units, 85));
        params.put("covMin", units <= 2 ? units : minimumCovered(units, 70));
        return new Coverage(String.join("\n               + ", covered), exact,
                "(case when ref_exact or covered >= :covFull then 3 when covered >= :covHigh then 2 else 1 end)",
                "ref_exact or covered >= :covMin");
    }

    /** Menor número de unidades cubiertas que alcanza {@code percent} % de {@code units} (aritmética entera). */
    static int minimumCovered(int units, int percent) {
        return (units * percent + 99) / 100;
    }

    /** Nivel de vehículo interpretado (ver la clase); {@code 0} constante sin vehículo interpretado. */
    static String vehicleLevel(SearchCriteria.Hints hints, Map<String, Object> params) {
        if (hints.makeId() == null && hints.modelId() == null) {
            return "0";
        }
        // Los datos de compatibilidad mandan sobre la mención de la marca: «para Chevrolet Spark» con regla de Spark es
        // de otro vehículo si se pidió Tracker. La mención del modelo pedido sí cuenta aunque haya datos (incompletos).
        String noData = "cardinality(sl.vehicle_make_ids) = 0";
        List<String> cases = new ArrayList<>();
        if (hints.modelId() != null) {
            params.put("hintModelId", hints.modelId());
            cases.add("when sl.vehicle_model_ids @> array[cast(:hintModelId as uuid)] then 4");
        }
        if (hints.makeId() != null) {
            params.put("hintMakeId", hints.makeId());
            if (hints.modelId() != null) {
                // Regla de toda la marca: el índice guarda la marca en vehicle_model_ids.
                cases.add("when sl.vehicle_model_ids @> array[cast(:hintMakeId as uuid)]"
                        + " or sl.mentioned_model_ids @> array[cast(:hintModelId as uuid)] then 3");
            } else {
                cases.add("when sl.vehicle_make_ids @> array[cast(:hintMakeId as uuid)] then 3");
            }
            cases.add("when " + noData + " and sl.mentioned_make_ids @> array[cast(:hintMakeId as uuid)] then 2");
        } else {
            cases.add("when sl.mentioned_model_ids @> array[cast(:hintModelId as uuid)] then 3");
        }
        cases.add("when sl.universal or (" + noData + " and cardinality(sl.mentioned_make_ids) = 0) then 1");
        return "(case " + String.join("\n                 ", cases) + "\n                 else 0 end)";
    }

    /** {@code 1} si {@code year_from/year_to} se solapan con el rango de años interpretado. */
    private static String yearCovered(SearchCriteria.Hints hints, Map<String, Object> params) {
        if (hints.year() == null) {
            return "0";
        }
        params.put("hintYearFrom", hints.yearFrom());
        params.put("hintYearTo", hints.yearTo());
        return "(case when (sl.year_from is not null or sl.year_to is not null)"
                + " and coalesce(sl.year_from, :hintYearTo) <= :hintYearTo"
                + " and coalesce(sl.year_to, :hintYearFrom) >= :hintYearFrom then 1 else 0 end)";
    }

    private static String cityMatch(SearchCriteria.Hints hints, Map<String, Object> params) {
        if (hints.cityCode() == null) {
            return "0";
        }
        params.put("hintCityCode", hints.cityCode());
        return "(case when sl.city_code = :hintCityCode then 1 else 0 end)";
    }

    /** Orden de relevancia (lexicográfico), con la cercanía como último desempate si hay ubicación. */
    static String relevance(SearchCriteria criteria) {
        String nearby = criteria.hasLocation() ? "(1.0 / (1.0 + coalesce(distance_km, 0) / 25.0)) desc, " : "";
        String learned = criteria.hints().learnedCategoryId() == null ? "" : "cat_ok desc, ";
        return "cov_tier desc, ref_exact desc, vehicle_level desc, year_ok desc, " + learned
                + "city_ok desc, text_rank desc, "
                + "(stock > 0) desc, coalesce(store_rating, 0) desc, " + nearby + "product_id";
    }

    private static String orderBy(SearchCriteria criteria) {
        String relevance = relevance(criteria);
        return switch (criteria.sort() == null ? SearchSort.RELEVANCE : criteria.sort()) {
            case RELEVANCE -> relevance;
            case PRICE_ASC -> "price asc, product_id";
            case PRICE_DESC -> "price desc, product_id";
            case NEAREST -> "distance_km asc nulls last, product_id";
            case NEWEST -> "published_at desc, product_id";
            case RATING -> "store_rating desc nulls last, " + relevance;
            case PRODUCT_RATING -> "product_rating desc nulls last, product_review_count desc, " + relevance;
            case NAME -> "name asc, product_id";
        };
    }

    /** Condiciones del {@code WHERE} de los candidatos sin las de las facetas de {@code skip}. Nunca vacía. */
    static List<String> conditions(SearchCriteria c, Map<String, Object> params, Set<Facet> skip) {
        List<String> where = new ArrayList<>();
        where.add("true");
        if (c.fuzzyText() != null) {
            where.add(":fuzzy <% sl.search_text");
            params.put("fuzzy", c.fuzzyText());
        } else if (c.hasTerms()) {
            where.add("sl.document @@ " + candidateQuery(c, params));
        } else {
            if (c.tsQuery() != null) {
                where.add("sl.document @@ " + tsquery("tsq"));
                params.put("tsq", c.tsQuery());
            }
            if (c.reference() != null) {
                // Referencia, SKU o número de parte: coincidencia en el peso A (pesos {D, C, B, A}).
                where.add("sl.document @@ " + plainTsquery("reference"));
                where.add(REFERENCE_WEIGHT_A + " > 0");
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
            // Marca interpretada: fuera el nivel 0 (otro vehículo), antes del tope de candidatos.
            where.add(vehicleLevel(hints, params) + " > 0");
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
