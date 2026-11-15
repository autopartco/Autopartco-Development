package com.autopartco.backend.core.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/** Sin BD: qué considera visible el índice de servicios, el upsert por lotes y el borrado de lo que ya no lo es. */
class ServiceSearchIndexerTest {
    private static final UUID SERVICE = UUID.randomUUID();

    private final SearchFakeJdbc jdbc = new SearchFakeJdbc();
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final ServiceSearchIndexer indexer = new ServiceSearchIndexer(jdbc.client, transactions,
            ServiceSearchIndexerPromotionTest.pricing(java.util.List.of()));

    @Test
    void onlyActiveServicesOfPublishedStoresInActiveBranchesAreIndexed() {
        indexer.reindexServices(List.of(SERVICE));

        SearchFakeJdbc.Call upsert = jdbc.callsMatching(sql -> sql.startsWith("insert into search_services")).getFirst();
        assertThat(upsert.sql())
                .contains("where ss.active and ss.id in (?)",
                        "join states st on st.id = s.state and st.deleted_at is null and st.code = 'ACTIVE'",
                        "join store s on s.id = ss.store_id and s.deleted_at is null",
                        "join store_service_branches sb on sb.service_id = ss.id",
                        "join states bs on bs.id = b.state and bs.deleted_at is null and bs.code = 'ACTIVE'",
                        "b.deleted_at is null and b.store_id = ss.store_id")
                .doesNotContain(SERVICE.toString());
        assertThat(upsert.positional()).containsExactly(SERVICE);
    }

    @Test
    void upsertBuildsTheWeightedDocumentLocationAndRating() {
        indexer.reindexServices(List.of(SERVICE));

        String sql = jdbc.callsMatching(sql2 -> sql2.startsWith("insert into search_services")).getFirst().sql();
        assertThat(sql)
                .contains("on conflict (service_key) do update set",
                        "ss.id::text || ':' || sb.branch_id::text",
                        "setweight(to_tsvector('public.spanish_unaccent'::regconfig, ss.name), 'A')",
                        "concat_ws(' ', ss.description, s.business_name)), 'C'",
                        "public.immutable_unaccent(lower(concat_ws(' ', ss.name,",
                        "l.municipality_code, gm.department_code",
                        "left join geo_municipalities gm on gm.code = l.municipality_code",
                        "from store_reviews r", "r.deleted_at is null and r.hidden_at is null",
                        "ss.price_from, ss.price_from, false, ss.booking_enabled");
        // Sin horario semanal en la sede no hay franjas: el servicio se indexa sin agenda ahí.
        assertThat(sql).contains("ss.booking_enabled and (b.opening_hours is not null and exists (",
                "jsonb_array_length(d.v) > 0", "'lunes','martes'").doesNotContain("{weeklyHours}");
        // La categoría (B) lleva etiqueta y sinónimos en español como constantes del código, nunca del usuario.
        assertThat(sql).contains("when 'ALIGNMENT_BALANCING' then 'Alineación y balanceo alineacion balanceo alinear'")
                .contains("when 'LUBRICATION' then 'Lubricación y cambio de aceite cambio de aceite lubricacion "
                        + "aceite y filtro'")
                .contains("when 'OTHER' then 'Otros servicios'")
                .doesNotContain("{categoryText}", "{ids}");
    }

    @Test
    void whatTheBatchDidNotTouchIsDeletedInTheSameTransaction() {
        indexer.reindexServices(List.of(SERVICE));

        SearchFakeJdbc.Call delete = jdbc.callsMatching(sql -> sql.startsWith("delete from search_services")).getFirst();
        assertThat(delete.sql())
                .isEqualTo("delete from search_services where service_id in (?) and updated_at < now()");
        assertThat(delete.positional()).containsExactly(SERVICE);
        verify(transactions).commit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void batchesOf200AndIgnoresNullsAndDuplicates() {
        List<UUID> ids = IntStream.range(0, 450).mapToObj(i -> UUID.randomUUID()).toList();
        java.util.List<UUID> withNoise = new java.util.ArrayList<>(ids);
        withNoise.add(null);
        withNoise.add(ids.getFirst());

        indexer.reindexServices(withNoise);

        assertThat(jdbc.callsMatching(sql -> sql.startsWith("insert into search_services"))).hasSize(3);
        assertThat(jdbc.callsMatching(sql -> sql.startsWith("delete from search_services"))).hasSize(3);
        verify(transactions, times(3)).commit(org.mockito.ArgumentMatchers.any());
        assertThat(jdbc.callsMatching(sql -> sql.startsWith("insert")).get(0).positional()).hasSize(200);
        assertThat(jdbc.callsMatching(sql -> sql.startsWith("insert")).get(2).positional()).hasSize(50);
        assertThat(indexer.reindexServices(null)).isZero();
        assertThat(indexer.reindexServices(List.of())).isZero();
    }

    @Test
    void fullReindexSweepsEveryServiceAndRemovesWhatItDidNotTouch() {
        OffsetDateTime startedAt = OffsetDateTime.parse("2026-11-13T08:00:00Z");
        jdbc.scalar(sql -> sql.equals(ServiceSearchIndexer.NOW_SQL), startedAt);
        jdbc.rows(sql -> sql.equals(ServiceSearchIndexer.ALL_SERVICES_SQL),
                call -> List.of(Map.of("id", SERVICE)));

        indexer.reindexAll();

        assertThat(jdbc.callsMatching(sql -> sql.startsWith("insert into search_services"))).hasSize(1);
        SearchFakeJdbc.Call sweep = jdbc.callsMatching(sql -> sql.equals(ServiceSearchIndexer.DELETE_UNTOUCHED_SQL))
                .getFirst();
        assertThat(sweep.positional()).containsExactly(startedAt);
        assertThat(ServiceSearchIndexer.ALL_SERVICES_SQL).contains("select service_id from search_services");
    }

    @Test
    void storeAndBranchChangesReindexTheirServicesIncludingTheOnesAlreadyIndexed() {
        UUID store = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        UUID indexedOnly = UUID.randomUUID();
        jdbc.rows(sql -> sql.equals(ServiceSearchIndexer.STORE_SERVICES_SQL),
                call -> List.of(Map.of("id", SERVICE), Map.of("id", indexedOnly)));
        jdbc.rows(sql -> sql.equals(ServiceSearchIndexer.BRANCH_SERVICES_SQL),
                call -> List.of(Map.of("service_id", SERVICE)));

        indexer.reindexStore(store);
        indexer.reindexBranch(branch);

        SearchFakeJdbc.Call storeLookup = jdbc.callsMatching(sql -> sql.equals(ServiceSearchIndexer.STORE_SERVICES_SQL))
                .getFirst();
        assertThat(storeLookup.positional()).containsExactly(store, store);
        SearchFakeJdbc.Call branchLookup = jdbc.callsMatching(sql -> sql.equals(ServiceSearchIndexer.BRANCH_SERVICES_SQL))
                .getFirst();
        assertThat(branchLookup.positional()).containsExactly(branch, branch);
        List<SearchFakeJdbc.Call> upserts = jdbc.callsMatching(sql -> sql.startsWith("insert into search_services"));
        assertThat(upserts.get(0).positional()).containsExactly(SERVICE, indexedOnly);
        assertThat(upserts.get(1).positional()).containsExactly(SERVICE);
    }
}
