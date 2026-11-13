package com.autopartco.backend.core.advertising;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

/**
 * Almacenes recomendados del mapa de cobertura: campañas {@code ACTIVE} vigentes hoy (Bogotá) con
 * {@link AdPlacement#MAP_FEATURED}, de un almacén {@code ACTIVE}. Una campaña por almacén; como mucho
 * {@code MAP_FEATURED.slots()} almacenes, en orden aleatorio para rotar cuando hay más. Nunca lanza: si la consulta
 * falla, el mapa sale sin recomendados.
 */
@Slf4j
@Service
public class MapFeaturedStores {
    static final String CAMPAIGNS_SQL = """
            select distinct on (c.store_id) c.id as campaign_id, c.store_id
            from ad_campaigns c
            join store s on s.id = c.store_id and s.deleted_at is null
            join states ss on ss.id = s.state and ss.deleted_at is null and ss.code = 'ACTIVE'
            where c.status = 'ACTIVE'
              and c.starts_on <= :today and c.ends_on >= :today
              and 'MAP_FEATURED' = any (c.placements)
              and c.store_id in (:storeIds)
            order by c.store_id, random()
            """;

    private final JdbcClient jdbcClient;
    private final AdTokenSigner signer;
    private final Clock clock;

    @Autowired
    public MapFeaturedStores(JdbcClient jdbcClient, AdTokenSigner signer) {
        this(jdbcClient, signer, Clock.systemUTC());
    }

    MapFeaturedStores(JdbcClient jdbcClient, AdTokenSigner signer, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.signer = signer;
        this.clock = clock;
    }

    /** Recomendados entre {@code candidateStoreIds} (almacenes con sedes en el área); vacío si no hay ninguno. */
    @Transactional(readOnly = true)
    public List<MapFeaturedPick> pick(Collection<UUID> candidateStoreIds) {
        if (candidateStoreIds == null || candidateStoreIds.isEmpty()) {
            return List.of();
        }
        List<Row> rows;
        try {
            rows = jdbcClient.sql(CAMPAIGNS_SQL)
                    .param("today", AdText.today(clock))
                    .param("storeIds", List.copyOf(candidateStoreIds))
                    .query((rs, rowNum) -> new Row(rs.getObject("campaign_id", UUID.class),
                            rs.getObject("store_id", UUID.class)))
                    .list();
        } catch (RuntimeException failure) {
            log.warn("Map featured stores unavailable: {}", failure.getClass().getSimpleName());
            return List.of();
        }
        List<Row> shuffled = new ArrayList<>(rows);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        return shuffled.stream()
                .limit(AdPlacement.MAP_FEATURED.slots())
                .map(row -> new MapFeaturedPick(row.campaignId(), row.storeId(),
                        signer.sign(row.campaignId(), null, AdPlacement.MAP_FEATURED)))
                .toList();
    }

    private record Row(UUID campaignId, UUID storeId) {
    }
}
