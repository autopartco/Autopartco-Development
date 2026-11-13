package com.autopartco.backend.core.search;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.OpeningHours;
import com.autopartco.backend.core.organization.ServiceCategory;
import com.autopartco.backend.core.organization.StoreBrand;
import com.autopartco.backend.shared.location.ColombiaBounds;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Búsqueda de servicios sobre {@code search_services} ({@link ServiceSearchSql}): un resultado por servicio con su
 * sucursal más cercana. La consulta SQL filtra, ordena y pagina; después solo la página se hidrata con los datos del
 * servicio, el almacén y la sucursal. Interpreta {@code q} como {@link SearchService}: la ciudad reconocida filtra
 * (con respaldo sin ella si no hay resultados), la categoría reconocida ordena; si ningún servicio tiene todas las
 * palabras se repite con trigramas. «Abierto ahora» usa {@link OpeningHours#openNow} con hora de Bogotá.
 */
@Slf4j
@Service
public class ServiceSearchService {
    static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    static final int MAX_RELATED = 6;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> HOURS = new TypeReference<>() {
    };

    static final String OPEN_CANDIDATES_SQL = """
            select b.id as branch_id, b.opening_hours::text as opening_hours
            from branches b
            where b.deleted_at is null and b.opening_hours is not null
              and b.id in (select branch_id from search_services)
            """;

    static final String HYDRATE_SQL = """
            select ss.service_key, s.id as service_id, s.name, s.category, s.description, s.image_url,
                   s.duration_minutes, ss.price_from, ss.base_price_from, ss.has_promotion, ss.booking_enabled,
                   ss.store_rating, st.id as store_id, st.business_name, st.logo_path, st.business_type,
                   b.id as branch_id, b.name as branch_name, b.opening_hours::text as opening_hours,
                   l.city, l.address_line, l.address_complement, l.latitude, l.longitude
            from search_services ss
            join store_services s on s.id = ss.service_id
            join store st on st.id = ss.store_id
            join branches b on b.id = ss.branch_id
            left join locations l on l.id = b.location_id
            where ss.service_key in (:keys)
            """;

    static final String BEST_BRANCH_SQL = """
            select ss.service_key, ss.service_id, ss.branch_id, %s as distance_km
            from search_services ss
            where ss.service_id = :serviceId
            order by %s
            limit 1
            """;

    static final String CATEGORY_COUNTS_SQL = """
            select ss.category, count(distinct ss.service_id) as n
            from search_services ss
            where (cast(:cityCode as text) is null or ss.city_code = cast(:cityCode as text))
            group by ss.category
            """;

    private record Row(String key, UUID serviceId, UUID branchId, Double distanceKm, long total) {
    }

    private record Page(List<Row> rows, long total) {
    }

    private final JdbcClient jdbcClient;
    private final QueryParser parser;
    private final Clock clock;

    @Autowired
    public ServiceSearchService(JdbcClient jdbcClient, QueryParser parser) {
        this(jdbcClient, parser, Clock.system(BOGOTA));
    }

    /** {@code clock} fija la hora de «abierto ahora» (la zona es la de Bogotá). */
    ServiceSearchService(JdbcClient jdbcClient, QueryParser parser, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.parser = parser;
        this.clock = clock.withZone(BOGOTA);
    }

    /** {@code GET /marketplace/services/search}: valida (mismos códigos que {@code /search}), busca y hidrata. */
    @Transactional(readOnly = true)
    public ServiceSearchResult search(ServiceSearchQuery query) {
        query.validate();
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        ParsedQuery parsed = query.q() == null ? ParsedQuery.EMPTY : parser.parse(query.q(), true);
        // La ciudad reconocida en q filtra solo si el cliente no fijó ciudad ni departamento.
        String interpretedCity = query.cityCode() == null && query.departmentCode() == null && parsed.city() != null
                ? parsed.city().code() : null;
        ServiceSearchSql.Criteria criteria = criteria(query, parsed, interpretedCity);
        Page found = fetch(criteria, page, size);
        if (found.total() == 0 && interpretedCity != null) {
            ServiceSearchSql.Criteria withoutCity = criteria.withoutCity();
            Page anywhere = fetch(withoutCity, page, size);
            if (anywhere.total() > 0) {
                criteria = withoutCity;
                found = anywhere;
            }
        }
        if (found.total() == 0 && parsed.tsQuery() != null && parsed.fuzzyText() != null) {
            ServiceSearchSql.Criteria similar = criteria.fuzzy(parsed.fuzzyText());
            jdbcClient.sql(ServiceSearchSql.FUZZY_THRESHOLD_SQL).query(String.class).single();
            Page similarPage = fetch(similar, page, size);
            if (similarPage.total() > 0) {
                criteria = similar;
                found = similarPage;
            }
        }
        List<ServiceHit> items = hydrate(found.rows());
        ServiceSearchResult.Facets facets = query.facetsOrDefault() ? facets(criteria) : null;
        return new ServiceSearchResult(items, page, size, found.total(), facets);
    }

    /** Servicio visible (en el índice) con su sucursal más cercana al punto, o la primera con ubicación. */
    @Transactional(readOnly = true)
    public Optional<ServiceHit> find(UUID serviceId, BigDecimal lat, BigDecimal lng) {
        boolean point = lat != null && lng != null && ColombiaBounds.contains(lat, lng);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("serviceId", serviceId);
        if (point) {
            params.put("lat", lat.doubleValue());
            params.put("lng", lng.doubleValue());
        }
        String sql = BEST_BRANCH_SQL.formatted(point ? ServiceSearchSql.DISTANCE_SQL : "null::double precision",
                point ? "distance_km asc nulls last, ss.service_key" : "(ss.latitude is null) asc, ss.service_key");
        List<Row> rows = jdbcClient.sql(sql).params(params).query((rs, row) -> row(rs, 0)).list();
        return hydrate(rows).stream().findFirst();
    }

    /**
     * Hasta {@code limit} (máximo {@value #MAX_RELATED}) servicios de la misma categoría, sin él mismo, los más cerca
     * del punto del visitante o, si no hay, de la sucursal del servicio; sin punto, los mejor calificados.
     */
    @Transactional(readOnly = true)
    public List<ServiceHit> related(ServiceHit hit, BigDecimal lat, BigDecimal lng, int limit) {
        BigDecimal pointLat = lat != null && lng != null ? lat : hit.branch().latitude();
        BigDecimal pointLng = lat != null && lng != null ? lng : hit.branch().longitude();
        boolean point = pointLat != null && pointLng != null && ColombiaBounds.contains(pointLat, pointLng);
        ServiceSearchQuery query = ServiceSearchQuery.builder()
                .category(hit.category())
                .excludeServiceId(hit.id())
                .lat(point ? pointLat : null).lng(point ? pointLng : null)
                .sort(point ? ServiceSearchQuery.Sort.NEAREST : ServiceSearchQuery.Sort.RATING)
                .page(0).size(Math.max(1, Math.min(limit, MAX_RELATED))).facets(false)
                .build();
        return search(query).items();
    }

    /** Servicios por categoría (opcionalmente solo de una ciudad), de más a menos; solo categorías con servicios. */
    @Transactional(readOnly = true)
    public List<ServiceSearchResult.CategoryCount> categories(String cityCode) {
        String city = cityCode == null || cityCode.isBlank() ? null : cityCode.strip();
        if (city != null && !city.matches("\\d{5}")) {
            throw new com.autopartco.backend.shared.error.BadRequestException("SEARCH_PLACE_INVALID",
                    "La ciudad (5 dígitos) no tiene el formato DIVIPOLA.");
        }
        List<ServiceSearchResult.CategoryCount> counts = new ArrayList<>(jdbcClient.sql(CATEGORY_COUNTS_SQL)
                .param("cityCode", city)
                .query((rs, row) -> new ServiceSearchResult.CategoryCount(
                        category(rs.getString("category")), rs.getLong("n")))
                .list().stream().filter(count -> count.category() != null && count.count() > 0).toList());
        counts.sort(java.util.Comparator.comparingLong(ServiceSearchResult.CategoryCount::count).reversed()
                .thenComparing(count -> count.category().ordinal()));
        return counts;
    }

    private ServiceSearchSql.Criteria criteria(ServiceSearchQuery query, ParsedQuery parsed, String interpretedCity) {
        return new ServiceSearchSql.Criteria(
                parsed.tsQuery(),
                null,
                parsed.serviceCategory(),
                query.category(),
                query.cityCode() != null ? query.cityCode() : interpretedCity,
                query.departmentCode(),
                query.lat() == null ? null : query.lat().doubleValue(),
                query.lng() == null ? null : query.lng().doubleValue(),
                query.radiusKm(),
                query.storeId(),
                query.priceMax(),
                Boolean.TRUE.equals(query.onSale()),
                Boolean.TRUE.equals(query.bookable()),
                Boolean.TRUE.equals(query.openNow()) ? openBranchIds() : null,
                query.excludeServiceId(),
                query.sortOrDefault());
    }

    /** Sucursales con horario semanal que ahora están abiertas (hora de Bogotá). */
    Set<UUID> openBranchIds() {
        Set<UUID> open = new HashSet<>();
        jdbcClient.sql(OPEN_CANDIDATES_SQL).query((rs, row) -> {
            if (Boolean.TRUE.equals(OpeningHours.openNow(hours(rs.getString("opening_hours")), clock))) {
                open.add(rs.getObject("branch_id", UUID.class));
            }
            return row;
        }).list();
        return open;
    }

    private Page fetch(ServiceSearchSql.Criteria criteria, int page, int size) {
        long offset = (long) page * size;
        ServiceSearchSql.Statement statement = ServiceSearchSql.page(criteria, size, offset);
        List<Row> rows = jdbcClient.sql(statement.sql()).params(statement.params())
                .query((rs, row) -> row(rs, rs.getLong("total"))).list();
        if (!rows.isEmpty()) {
            return new Page(rows, rows.getFirst().total());
        }
        if (page == 0) {
            return new Page(List.of(), 0);
        }
        // Página fuera de rango: el total real lo da un conteo aparte.
        ServiceSearchSql.Statement count = ServiceSearchSql.count(criteria);
        Long total = jdbcClient.sql(count.sql()).params(count.params()).query(Long.class).single();
        return new Page(List.of(), total == null ? 0 : total);
    }

    private ServiceSearchResult.Facets facets(ServiceSearchSql.Criteria criteria) {
        ServiceSearchSql.Statement categories = ServiceSearchSql.categoryFacets(criteria);
        List<ServiceSearchResult.CategoryCount> categoryCounts = jdbcClient.sql(categories.sql())
                .params(categories.params())
                .query((rs, row) -> new ServiceSearchResult.CategoryCount(
                        category(rs.getString("category")), rs.getLong("n")))
                .list().stream().filter(count -> count.category() != null).toList();
        ServiceSearchSql.Statement cities = ServiceSearchSql.cityFacets(criteria);
        List<SearchResult.CityCount> cityCounts = jdbcClient.sql(cities.sql()).params(cities.params())
                .query((rs, row) -> new SearchResult.CityCount(rs.getString("city_code"), rs.getString("city_name"),
                        rs.getLong("n")))
                .list();
        return new ServiceSearchResult.Facets(categoryCounts, cityCounts);
    }

    /** Hidrata la página conservando su orden; una clave que ya no existe (carrera con el índice) se omite. */
    private List<ServiceHit> hydrate(List<Row> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, ServiceHit> byKey = new LinkedHashMap<>();
        jdbcClient.sql(HYDRATE_SQL).param("keys", rows.stream().map(Row::key).toList())
                .query((rs, row) -> {
                    byKey.put(rs.getString("service_key"), hit(rs));
                    return row;
                }).list();
        List<ServiceHit> hits = new ArrayList<>();
        for (Row row : rows) {
            ServiceHit hit = byKey.get(row.key());
            if (hit != null) {
                hits.add(row.distanceKm() == null ? hit : withDistance(hit, row.distanceKm()));
            }
        }
        return hits;
    }

    private ServiceHit hit(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID storeId = rs.getObject("store_id", UUID.class);
        String address = address(rs.getString("address_line"), rs.getString("address_complement"));
        ServiceHit.Store store = new ServiceHit.Store(storeId, rs.getString("business_name"),
                StoreBrand.url(storeId, StoreBrand.LOGO, rs.getString("logo_path")),
                rs.getObject("store_rating", BigDecimal.class), rs.getString("business_type"));
        ServiceHit.Branch branch = new ServiceHit.Branch(rs.getObject("branch_id", UUID.class),
                rs.getString("branch_name"), rs.getString("city"), address,
                rs.getObject("latitude", BigDecimal.class), rs.getObject("longitude", BigDecimal.class),
                OpeningHours.openNow(hours(rs.getString("opening_hours")), clock));
        return new ServiceHit(rs.getObject("service_id", UUID.class), rs.getString("name"),
                category(rs.getString("category")), rs.getString("description"), rs.getString("image_url"),
                rs.getObject("price_from", BigDecimal.class), rs.getObject("base_price_from", BigDecimal.class),
                rs.getBoolean("has_promotion"), rs.getObject("duration_minutes", Integer.class),
                rs.getBoolean("booking_enabled"), store, branch, null);
    }

    private static ServiceHit withDistance(ServiceHit hit, double km) {
        return new ServiceHit(hit.id(), hit.name(), hit.category(), hit.description(), hit.imageUrl(), hit.priceFrom(),
                hit.basePriceFrom(), hit.hasPromotion(), hit.durationMinutes(), hit.bookingEnabled(), hit.store(),
                hit.branch(), BigDecimal.valueOf(km).setScale(1, RoundingMode.HALF_UP));
    }

    private static Row row(java.sql.ResultSet rs, long total) throws java.sql.SQLException {
        return new Row(rs.getString("service_key"), rs.getObject("service_id", UUID.class),
                rs.getObject("branch_id", UUID.class), rs.getObject("distance_km", Double.class), total);
    }

    private static String address(String line, String complement) {
        String base = line == null ? "" : line.trim();
        return complement == null || complement.isBlank() ? base : base + " " + complement.trim();
    }

    private static ServiceCategory category(String value) {
        try {
            return value == null ? null : ServiceCategory.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static Map<String, Object> hours(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, HOURS);
        } catch (com.fasterxml.jackson.core.JacksonException unreadable) {
            return null;
        }
    }
}
