package com.autopartco.backend.core.advertising;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Carga de la BD los candidatos de una ubicación: productos de campañas {@code ACTIVE} vigentes en {@code today}, con
 * el producto, el almacén y al menos un listado publicado (mismos estados que {@code core.catalog.ListingQueries}:
 * producto, almacén y sucursal {@code ACTIVE}, sin borrar y con stock). Un producto que deja de estar publicado sale
 * de la entrega sin reembolso.
 */
@Slf4j
@Component
public class AdCandidateSource {
    static final String CANDIDATES_SQL = """
            select c.id as campaign_id, c.store_id, c.targeting::text as targeting, p.id as product_id,
                   p.name as product_name, p.category_id
            from ad_campaigns c
            join ad_campaign_products cp on cp.campaign_id = c.id
            join products p on p.id = cp.product_id and p.deleted_at is null and p.store_id = c.store_id
            join states ps on ps.id = p.state_id and ps.deleted_at is null and ps.code = 'ACTIVE'
            join store s on s.id = c.store_id and s.deleted_at is null
            join states ss on ss.id = s.state and ss.deleted_at is null and ss.code = 'ACTIVE'
            where c.status = 'ACTIVE'
              and c.starts_on <= :today and c.ends_on >= :today
              and :placement = any (c.placements)
              and exists (select 1 from inventory i
                          join branches b on b.id = i.branch_id and b.deleted_at is null
                          join states bs on bs.id = b.state and bs.deleted_at is null and bs.code = 'ACTIVE'
                          where i.product_id = p.id and i.deleted_at is null and i.stock > 0)
            order by c.id, p.id
            """;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;

    public AdCandidateSource(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<AdCandidate> load(AdPlacement placement, LocalDate today) {
        return jdbcClient.sql(CANDIDATES_SQL)
                .param("today", today)
                .param("placement", placement.name())
                .query((rs, rowNum) -> new AdCandidate(
                        rs.getObject("campaign_id", java.util.UUID.class),
                        rs.getObject("store_id", java.util.UUID.class),
                        rs.getObject("product_id", java.util.UUID.class),
                        AdText.words(rs.getString("product_name")),
                        rs.getObject("category_id", java.util.UUID.class),
                        targeting(rs.getString("targeting"))))
                .list();
    }

    static AdTargeting targeting(String json) {
        if (json == null || json.isBlank()) {
            return AdTargeting.NONE;
        }
        try {
            return AdTargeting.fromJson(JSON.readValue(json, MAP));
        } catch (Exception unreadable) {
            log.warn("Ignoring unreadable ad targeting JSON");
            return AdTargeting.NONE;
        }
    }
}
