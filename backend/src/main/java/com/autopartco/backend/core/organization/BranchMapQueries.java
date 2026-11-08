package com.autopartco.backend.core.organization;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    static final int MAX_LIMIT = 8;

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
              and l.longitude between :west and :east
            order by %s
            limit :limit
            """;

    static final String DISTANCE_COLUMN = """
            ,
                   2 * 6371 * asin(sqrt(least(1.0,
                       power(sin(radians((l.latitude - :lat) / 2)), 2)
                       + cos(radians(:lat)) * cos(radians(l.latitude))
                         * power(sin(radians((l.longitude - :lng) / 2)), 2)))) as distance_km""";

    static final String NEAREST_SQL = BASE_SQL.formatted(DISTANCE_COLUMN, "distance_km asc, b.id");
    static final String RANDOM_SQL = BASE_SQL.formatted("", "random()");

    private final JdbcClient jdbcClient;

    /** Rectángulo ya recortado a Colombia; {@code lat}/{@code lng} ambos nulos o ambos presentes. */
    @Transactional(readOnly = true)
    public List<MapBranchView> find(BigDecimal south, BigDecimal west, BigDecimal north, BigDecimal east,
            BigDecimal lat, BigDecimal lng, int limit) {
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
        return jdbcClient.sql(nearest ? NEAREST_SQL : RANDOM_SQL)
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
                        nearest ? BigDecimal.valueOf(rs.getDouble("distance_km")).setScale(1, RoundingMode.HALF_UP) : null))
                .list();
    }
}
