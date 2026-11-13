package com.autopartco.backend.core.search;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.autopartco.backend.core.organization.ServiceCategory;

/**
 * Construye el SQL de búsqueda sobre {@code search_services} (una fila por servicio×sucursal). Los valores van siempre
 * como parámetros con nombre; el texto del SQL solo depende de qué filtros vienen.
 *
 * <ul>
 *   <li>Texto: {@code document @@ to_tsquery('public.spanish_unaccent', :tsq)}; respaldo con trigramas
 *   ({@code :fuzzy <% search_text}).</li>
 *   <li>Un resultado por servicio: {@code distinct on (service_id)} con la sucursal más cercana si hay punto; si no, la
 *   primera con ubicación y, si ninguna la tiene, cualquiera. Los filtros se aplican por fila: la sucursal elegida
 *   siempre cumple los filtros de ciudad, cercanía y «abierto ahora».</li>
 *   <li>Relevancia: {@code ts_rank_cd × (categoría interpretada ×2) × (1 + 0,1·calificación/5) × 1/(1 + km/25)}.</li>
 *   <li>Cercanía: caja de coordenadas y luego Haversine, como {@link SearchSql}.</li>
 * </ul>
 */
final class ServiceSearchSql {
    static final String CONFIG = "'public.spanish_unaccent'::regconfig";
    static final String FUZZY_THRESHOLD_SQL = SearchSql.FUZZY_THRESHOLD_SQL;
    static final String CATEGORY_BOOST = "2.0";
    static final int MAX_CITY_FACETS = 20;

    /** Facetas cuyo propio filtro se omite al contarlas. */
    enum Facet { CATEGORY, CITY }

    /**
     * Filtros ya resueltos. {@code openBranchIds} nulo = sin filtro de «abierto ahora»; vacío = ninguna sucursal abierta.
     * {@code hintCategory} (interpretada de {@code q}) solo ordena.
     */
    record Criteria(
            String tsQuery,
            String fuzzyText,
            ServiceCategory hintCategory,
            ServiceCategory category,
            String cityCode,
            String departmentCode,
            Double lat,
            Double lng,
            Integer radiusKm,
            UUID storeId,
            BigDecimal priceMax,
            boolean onSale,
            boolean bookable,
            Collection<UUID> openBranchIds,
            UUID excludeServiceId,
            ServiceSearchQuery.Sort sort
    ) {
        boolean hasLocation() {
            return lat != null && lng != null;
        }

        Criteria fuzzy(String text) {
            return new Criteria(null, text, hintCategory, category, cityCode, departmentCode, lat, lng, radiusKm,
                    storeId, priceMax, onSale, bookable, openBranchIds, excludeServiceId, sort);
        }

        Criteria withoutCity() {
            return new Criteria(tsQuery, fuzzyText, hintCategory, category, null, departmentCode, lat, lng, radiusKm,
                    storeId, priceMax, onSale, bookable, openBranchIds, excludeServiceId, sort);
        }
    }

    record Statement(String sql, Map<String, Object> params) {
    }

    private ServiceSearchSql() {
    }

    static final String DISTANCE_SQL = """
            2 * 6371 * asin(sqrt(least(1.0,
                power(sin(radians((ss.latitude - :lat) / 2)), 2)
                + cos(radians(:lat)) * cos(radians(ss.latitude))
                  * power(sin(radians((ss.longitude - :lng) / 2)), 2))))""";

    /** Página de claves ({@code service_key}) ordenadas, con distancia y total ({@code count(*) over ()}). */
    static Statement page(Criteria criteria, int limit, long offset) {
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> where = conditions(criteria, params, EnumSet.noneOf(Facet.class));
        String distance = criteria.hasLocation() ? DISTANCE_SQL : "null::double precision";
        String representative = criteria.hasLocation()
                ? "distance_km asc nulls last, service_key"
                : "no_location asc, service_key";
        String decay = criteria.hasLocation() ? " * (1.0 / (1.0 + coalesce(distance_km, 0) / 25.0))" : "";
        String sql = """
                with matched as (
                    select ss.service_key, ss.service_id, ss.branch_id, ss.price_from, ss.store_rating,
                           (ss.latitude is null) as no_location,
                           %s as distance_km,
                           %s as text_rank,
                           %s as hint_boost
                    from search_services ss
                    where %s
                ), best as (
                    select distinct on (service_id) *
                    from matched
                    order by service_id, %s
                ), scored as (
                    select best.*, text_rank * hint_boost * (1 + 0.1 * coalesce(store_rating, 0) / 5)%s as score
                    from best
                )
                select service_key, service_id, branch_id, distance_km, count(*) over () as total
                from scored
                order by %s
                limit :limit offset :offset
                """.formatted(distance, textRank(criteria, params), hintBoost(criteria, params),
                String.join("\n      and ", where), representative, decay, orderBy(criteria.sort()));
        params.put("limit", limit);
        params.put("offset", offset);
        return new Statement(sql, params);
    }

    /** Servicios distintos que cumplen los filtros (cuando la página pedida sale vacía y hace falta el total). */
    static Statement count(Criteria criteria) {
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> where = conditions(criteria, params, EnumSet.noneOf(Facet.class));
        return new Statement("select count(distinct ss.service_id) from search_services ss where "
                + String.join("\n  and ", where), params);
    }

    /** Servicios por categoría con todos los filtros salvo el de categoría. */
    static Statement categoryFacets(Criteria criteria) {
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> where = conditions(criteria, params, EnumSet.of(Facet.CATEGORY));
        return new Statement("""
                select ss.category, count(distinct ss.service_id) as n
                from search_services ss
                where %s
                group by ss.category
                order by n desc, ss.category
                """.formatted(String.join("\n  and ", where)), params);
    }

    /** Servicios por ciudad con todos los filtros salvo el de ciudad. */
    static Statement cityFacets(Criteria criteria) {
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> where = conditions(criteria, params, EnumSet.of(Facet.CITY));
        where.add("ss.city_code is not null");
        params.put("cityLimit", MAX_CITY_FACETS);
        return new Statement("""
                select ss.city_code, gm.name as city_name, count(distinct ss.service_id) as n
                from search_services ss
                left join geo_municipalities gm on gm.code = ss.city_code
                where %s
                group by ss.city_code, gm.name
                order by n desc, gm.name
                limit :cityLimit
                """.formatted(String.join("\n  and ", where)), params);
    }

    private static String textRank(Criteria criteria, Map<String, Object> params) {
        if (criteria.fuzzyText() != null) {
            return "word_similarity(:fuzzy, ss.search_text)";
        }
        if (criteria.tsQuery() != null) {
            return "ts_rank_cd(ss.document, to_tsquery(" + CONFIG + ", :tsq))";
        }
        return "1.0";
    }

    private static String hintBoost(Criteria criteria, Map<String, Object> params) {
        if (criteria.hintCategory() == null) {
            return "1.0";
        }
        params.put("hintCategory", criteria.hintCategory().name());
        return "(case when ss.category = :hintCategory then " + CATEGORY_BOOST + " else 1.0 end)";
    }

    private static String orderBy(ServiceSearchQuery.Sort sort) {
        return switch (sort == null ? ServiceSearchQuery.Sort.RELEVANCE : sort) {
            case RELEVANCE -> "score desc, service_id";
            case NEAREST -> "distance_km asc nulls last, service_id";
            case PRICE_ASC -> "price_from asc nulls last, service_id";
            case RATING -> "store_rating desc nulls last, score desc, service_id";
        };
    }

    /** Condiciones del {@code WHERE} sin las de las facetas de {@code skip}. Nunca vacía. */
    static List<String> conditions(Criteria c, Map<String, Object> params, Set<Facet> skip) {
        List<String> where = new ArrayList<>();
        where.add("true");
        if (c.fuzzyText() != null) {
            where.add(":fuzzy <% ss.search_text");
            params.put("fuzzy", c.fuzzyText());
        } else if (c.tsQuery() != null) {
            where.add("ss.document @@ to_tsquery(" + CONFIG + ", :tsq)");
            params.put("tsq", c.tsQuery());
        }
        if (c.storeId() != null) {
            where.add("ss.store_id = :storeId");
            params.put("storeId", c.storeId());
        }
        if (c.excludeServiceId() != null) {
            where.add("ss.service_id <> :excludeServiceId");
            params.put("excludeServiceId", c.excludeServiceId());
        }
        if (c.departmentCode() != null) {
            where.add("ss.department_code = :departmentCode");
            params.put("departmentCode", c.departmentCode());
        }
        if (c.priceMax() != null) {
            where.add("ss.price_from <= :priceMax");
            params.put("priceMax", c.priceMax());
        }
        if (c.onSale()) {
            where.add("ss.has_promotion");
        }
        if (c.bookable()) {
            where.add("ss.booking_enabled");
        }
        if (c.openBranchIds() != null) {
            if (c.openBranchIds().isEmpty()) {
                where.add("false");
            } else {
                where.add("ss.branch_id in (:openBranchIds)");
                params.put("openBranchIds", List.copyOf(c.openBranchIds()));
            }
        }
        if (c.hasLocation()) {
            params.put("lat", c.lat());
            params.put("lng", c.lng());
            if (c.radiusKm() != null) {
                double latDelta = c.radiusKm() / SearchSql.KM_PER_DEGREE;
                double cos = Math.max(0.01, Math.cos(Math.toRadians(c.lat())));
                double lngDelta = c.radiusKm() / (SearchSql.KM_PER_DEGREE * cos);
                where.add("ss.latitude between :south and :north");
                where.add("ss.longitude between :west and :east");
                where.add(DISTANCE_SQL + " <= :radiusKm");
                params.put("south", c.lat() - latDelta);
                params.put("north", c.lat() + latDelta);
                params.put("west", c.lng() - lngDelta);
                params.put("east", c.lng() + lngDelta);
                params.put("radiusKm", c.radiusKm().doubleValue());
            }
        }
        if (!skip.contains(Facet.CATEGORY) && c.category() != null) {
            where.add("ss.category = :category");
            params.put("category", c.category().name());
        }
        if (!skip.contains(Facet.CITY) && c.cityCode() != null) {
            where.add("ss.city_code = :cityCode");
            params.put("cityCode", c.cityCode());
        }
        return where;
    }
}
