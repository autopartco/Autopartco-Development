package com.autopartco.backend.core.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Sin BD aislada: se verifica el SQL y los parámetros enviados a {@code JdbcClient} y el mapeo de filas. Los filtros
 * (sede activa, tienda publicada, coordenadas, borrado lógico) se comprueban sobre el texto del SQL.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class BranchMapQueriesTest {
    private static final BigDecimal S = new BigDecimal("4.5");
    private static final BigDecimal N = new BigDecimal("4.8");
    private static final BigDecimal W = new BigDecimal("-74.2");
    private static final BigDecimal E = new BigDecimal("-74.0");

    private final JdbcClient jdbcClient = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
    private final JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class, RETURNS_DEEP_STUBS);
    private final BranchMapQueries queries = new BranchMapQueries(jdbcClient);

    @org.junit.jupiter.api.BeforeEach
    void wire() {
        when(jdbcClient.sql(anyString())).thenReturn(statement);
    }

    @Test
    void nearestUsesHaversineOrderAndPassesPointAndBounds() {
        when(statement.params(anyMap()).query(any(RowMapper.class)).list()).thenReturn(List.of());

        queries.find(S, W, N, E, new BigDecimal("4.6"), new BigDecimal("-74.1"), 3);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcClient, org.mockito.Mockito.atLeastOnce()).sql(sql.capture());
        assertThat(sql.getValue())
                .contains("asin(sqrt(", "as distance_km", "order by distance_km asc, b.id", "limit :limit")
                .doesNotContain("random()");
        ArgumentCaptor<Map> params = ArgumentCaptor.forClass(Map.class);
        verify(statement, org.mockito.Mockito.atLeastOnce()).params(params.capture());
        assertThat((Map<String, Object>) params.getValue())
                .containsEntry("lat", new BigDecimal("4.6"))
                .containsEntry("lng", new BigDecimal("-74.1"))
                .containsEntry("south", S).containsEntry("north", N)
                .containsEntry("west", W).containsEntry("east", E)
                .containsEntry("limit", 3)
                .containsEntry("activeCode", "ACTIVE");
    }

    @Test
    void withoutPointUsesRandomOrderAndNoDistance() {
        when(statement.params(anyMap()).query(any(RowMapper.class)).list()).thenReturn(List.of());

        queries.find(S, W, N, E, null, null, 4);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcClient, org.mockito.Mockito.atLeastOnce()).sql(sql.capture());
        assertThat(sql.getValue()).contains("order by random()").doesNotContain("distance_km");
        ArgumentCaptor<Map> params = ArgumentCaptor.forClass(Map.class);
        verify(statement, org.mockito.Mockito.atLeastOnce()).params(params.capture());
        assertThat((Map<String, Object>) params.getValue()).doesNotContainKeys("lat", "lng");
    }

    @Test
    void sqlFiltersActiveBranchPublishedStoreCoordinatesAndSoftDeletes() {
        for (String sql : List.of(BranchMapQueries.NEAREST_SQL, BranchMapQueries.RANDOM_SQL)) {
            assertThat(sql).contains(
                    "join store s on s.id = b.store_id and s.deleted_at is null",
                    "join states ss on ss.id = s.state and ss.deleted_at is null and ss.code = :activeCode",
                    "join states bs on bs.id = b.state and bs.deleted_at is null and bs.code = :activeCode",
                    "join locations l on l.id = b.location_id and l.deleted_at is null",
                    "b.deleted_at is null",
                    "l.latitude is not null and l.longitude is not null",
                    "l.latitude between :south and :north",
                    "l.longitude between :west and :east");
            assertThat(sql).doesNotContain("number_phone", "address_line");
        }
    }

    @Test
    void limitIsCappedAtTwoHundred() {
        when(statement.params(anyMap()).query(any(RowMapper.class)).list()).thenReturn(List.of());

        queries.find(S, W, N, E, null, null, 500);

        ArgumentCaptor<Map> params = ArgumentCaptor.forClass(Map.class);
        verify(statement, org.mockito.Mockito.atLeastOnce()).params(params.capture());
        assertThat((Map<String, Object>) params.getValue()).containsEntry("limit", 200);
    }

    @Test
    void rowMapperRoundsDistanceToOneDecimalOrNull() throws Exception {
        ArgumentCaptor<RowMapper> mapper = ArgumentCaptor.forClass(RowMapper.class);
        when(statement.params(anyMap()).query(mapper.capture()).list()).thenReturn(List.of());
        UUID id = UUID.randomUUID();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("store_id", UUID.class)).thenReturn(id);
        when(rs.getObject("branch_id", UUID.class)).thenReturn(id);
        when(rs.getString("store_name")).thenReturn("Repuestos");
        when(rs.getString("branch_name")).thenReturn("Centro");
        when(rs.getString("area")).thenReturn(null);
        when(rs.getString("city")).thenReturn("Bogotá");
        when(rs.getString("department")).thenReturn("Cundinamarca");
        when(rs.getBigDecimal("latitude")).thenReturn(new BigDecimal("4.6097100"));
        when(rs.getBigDecimal("longitude")).thenReturn(new BigDecimal("-74.0817500"));
        when(rs.getDouble("distance_km")).thenReturn(1.5678);

        queries.find(S, W, N, E, new BigDecimal("4.6"), new BigDecimal("-74.1"), 4);
        MapBranchView withDistance = (MapBranchView) mapper.getValue().mapRow(rs, 0);
        assertThat(withDistance.distanceKm()).isEqualByComparingTo("1.6");
        assertThat(withDistance.area()).isNull();
        assertThat(withDistance.storeName()).isEqualTo("Repuestos");

        queries.find(S, W, N, E, null, null, 4);
        MapBranchView withoutDistance = (MapBranchView) mapper.getValue().mapRow(rs, 0);
        assertThat(withoutDistance.distanceKm()).isNull();
    }

    @Test
    void excludedStoresAddNotInFilterAndOnlyStoresAddInFilter() {
        when(statement.params(anyMap()).query(any(RowMapper.class)).list()).thenReturn(List.of());
        UUID store = UUID.randomUUID();

        queries.find(S, W, N, E, null, null, 100, List.of(store));
        queries.findForStores(List.of(store), S, W, N, E, null, null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcClient, org.mockito.Mockito.times(2)).sql(sql.capture());
        assertThat(sql.getAllValues().get(0)).contains("and s.id not in (:storeIds)", "order by random()");
        assertThat(sql.getAllValues().get(1)).contains("and s.id in (:storeIds)").doesNotContain("not in");
        ArgumentCaptor<Map> params = ArgumentCaptor.forClass(Map.class);
        verify(statement, org.mockito.Mockito.atLeastOnce()).params(params.capture());
        assertThat((Map<String, Object>) params.getValue()).containsEntry("storeIds", List.of(store))
                .containsEntry("limit", 200);
    }

    @Test
    void findForNoStoresSkipsTheQuery() {
        assertThat(queries.findForStores(List.of(), S, W, N, E, null, null)).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(jdbcClient);
    }

    @Test
    void storeIdsInUsesTheSameVisibilityFilters() {
        queries.storeIdsIn(S, W, N, E);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcClient).sql(sql.capture());
        assertThat(sql.getValue()).startsWith("select distinct s.id as store_id from branches b")
                .contains("ss.code = :activeCode", "bs.code = :activeCode", "l.latitude is not null",
                        "l.latitude between :south and :north");
    }
}
