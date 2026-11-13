package com.autopartco.backend.core.organization;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.shared.persistence.StateCodes;

import lombok.AllArgsConstructor;

/**
 * Read model de sedes para el mapa de cobertura (público). SQL nativo sobre {@code branches}, {@code locations},
 * {@code store} y {@code states}. Sede activa de un almacén publicado (mismo criterio que
 * {@code StoreService.published()}: estado {@code ACTIVE}), con coordenadas y sin borrado lógico. No expone teléfono
 * ni {@code address_line}, y no registra coordenadas en logs.
 */
@Service
@AllArgsConstructor
public class BranchMapQueries {
    static final int MAX_LIMIT = 200;

    static final String FROM_SQL = """
            from branches b
            join store s on s.id = b.store_id and s.deleted_at is null
            join states ss on ss.id = s.state and ss.deleted_at is null and ss.code = :activeCode
            join states bs on bs.id = b.state and bs.deleted_at is null and bs.code = :activeCode
            join locations l on l.id = b.location_id and l.deleted_at is null
            where b.deleted_at is null
              and l.latitude is not null and l.longitude is not null
              and l.latitude between :south and :north
              and l.longitude between :west and :east
            """;

    static final String STORE_IDS_SQL = "select distinct s.id as store_id " + FROM_SQL;

    static final String BASE_SQL = """
            select s.id as store_id, s.business_name as store_name, b.id as branch_id, b.name as branch_name,
                   l.address_complement as area, l.city, l.department, l.latitude, l.longitude%s
            from branches b
            join store s on s.id = b.store_id and s.deleted_at is null
            join states ss on ss.id = s.state and ss.deleted_at is null and ss.code = :activeCode
            join states bs on bs.id = b.state and bs.deleted_at is null and bs.code = :activeCode
            join locations l on l.id = b.location_id and l.deleted_at is null
            where b.deleted_at is null
              and l.latitude is not null and l.longitude is not null
              and l.latitude between :south and :north
              and l.longitude between :west and :east%s
            order by %s
            limit :limit
            """;

    static final String DISTANCE_COLUMN = """
            ,
                   2 * 6371 * asin(sqrt(least(1.0,
                       power(sin(radians((l.latitude - :lat) / 2)), 2)
                       + cos(radians(:lat)) * cos(radians(l.latitude))
                         * power(sin(radians((l.longitude - :lng) / 2)), 2)))) as distance_km""";

    static final String ONLY_STORES = " and s.id in (:storeIds)";
    static final String EXCEPT_STORES = " and s.id not in (:storeIds)";
    static final String NEAREST_SQL = sql(true, "");
    static final String RANDOM_SQL = sql(false, "");

    static String sql(boolean nearest, String storeFilter) {
        return BASE_SQL.formatted(nearest ? DISTANCE_COLUMN : "", storeFilter,
                nearest ? "distance_km asc, b.id" : "random()");
    }

    /** Almacenes con al menos una sede visible en el rectángulo (candidatos a «Recomendado»). */
    @Transactional(readOnly = true)
    public List<UUID> storeIdsIn(BigDecimal south, BigDecimal west, BigDecimal north, BigDecimal east) {
        return jdbcClient.sql(STORE_IDS_SQL)
                .param("activeCode", StateCodes.ACTIVE)
                .param("south", south)
                .param("north", north)
                .param("west", west)
                .param("east", east)
                .query((rs, row) -> rs.getObject("store_id", UUID.class))
                .list();
    }

    /** Sedes de {@code storeIds} dentro del rectángulo (hasta {@value #MAX_LIMIT}); vacío si no hay almacenes. */
    @Transactional(readOnly = true)
    public List<MapBranchView> findForStores(Collection<UUID> storeIds, BigDecimal south, BigDecimal west,
            BigDecimal north, BigDecimal east, BigDecimal lat, BigDecimal lng) {
        if (storeIds.isEmpty()) {
            return List.of();
        }
        return query(ONLY_STORES, storeIds, south, west, north, east, lat, lng, MAX_LIMIT);
    }

    private final JdbcClient jdbcClient;

    /** Rectángulo ya recortado a Colombia; {@code lat}/{@code lng} ambos nulos o ambos presentes. */
    @Transactional(readOnly = true)
    public List<MapBranchView> find(BigDecimal south, BigDecimal west, BigDecimal north, BigDecimal east,
            BigDecimal lat, BigDecimal lng, int limit) {
        return find(south, west, north, east, lat, lng, limit, List.of());
    }

    /** Como {@link #find(BigDecimal, BigDecimal, BigDecimal, BigDecimal, BigDecimal, BigDecimal, int)}, sin las sedes
     * de {@code excludedStoreIds} (los recomendados, que se piden aparte). */
    @Transactional(readOnly = true)
    public List<MapBranchView> find(BigDecimal south, BigDecimal west, BigDecimal north, BigDecimal east,
            BigDecimal lat, BigDecimal lng, int limit, Collection<UUID> excludedStoreIds) {
        return excludedStoreIds.isEmpty()
                ? query("", List.of(), south, west, north, east, lat, lng, limit)
                : query(EXCEPT_STORES, excludedStoreIds, south, west, north, east, lat, lng, limit);
    }

    private List<MapBranchView> query(String storeFilter, Collection<UUID> storeIds, BigDecimal south,
            BigDecimal west, BigDecimal north, BigDecimal east, BigDecimal lat, BigDecimal lng, int limit) {
        boolean nearest = lat != null && lng != null;
        Map<String, Object> params = new HashMap<>();
        params.put("activeCode", StateCodes.ACTIVE);
        params.put("south", south);
        params.put("north", north);
        params.put("west", west);
        params.put("east", east);
        params.put("limit", Math.max(1, Math.min(limit, MAX_LIMIT)));
        if (nearest) {
            params.put("lat", lat);
            params.put("lng", lng);
        }
        if (!storeFilter.isEmpty()) {
            params.put("storeIds", List.copyOf(storeIds));
        }
        return jdbcClient.sql(sql(nearest, storeFilter))
                .params(params)
                .query((rs, row) -> new MapBranchView(
                        rs.getObject("store_id", UUID.class),
                        rs.getString("store_name"),
                        rs.getObject("branch_id", UUID.class),
                        rs.getString("branch_name"),
                        rs.getString("area"),
                        rs.getString("city"),
                        rs.getString("department"),
                        rs.getBigDecimal("latitude"),
                        rs.getBigDecimal("longitude"),
                        nearest ? BigDecimal.valueOf(rs.getDouble("distance_km")).setScale(1, RoundingMode.HALF_UP) : null,
                        false,
                        null))
                .list();
    }
}
