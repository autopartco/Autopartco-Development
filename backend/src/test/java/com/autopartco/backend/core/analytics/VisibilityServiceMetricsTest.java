package com.autopartco.backend.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

/** {@code SERVICE_VIEW}, servicios más vistos y conteo de citas en el reporte de visibilidad. */
class VisibilityServiceMetricsTest {
    private final JdbcClient jdbcClient = mock(JdbcClient.class);
    private final UUID store = UUID.randomUUID();
    private final UUID service = UUID.randomUUID();
    private final DateRange range = new DateRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2));

    // --- Registro ----------------------------------------------------------------------------------------------------

    @Test
    void serviceViewWithoutServiceIsIgnored() {
        VisibilityRecorder recorder = new VisibilityRecorder(jdbcClient, "sal", "https://www.autopartco.co");

        assertThat(recorder.record(visit("SERVICE_VIEW", null))).isFalse();
        verify(jdbcClient, never()).sql(anyString());
    }

    @Test
    void serviceViewKeepsTheServiceAndTheSqlChecksItBelongsToTheStore() {
        Map<String, Object> params = new HashMap<>();
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
        when(jdbcClient.sql(VisibilityRecorder.INSERT_SQL)).thenReturn(statement);
        when(statement.param(anyString(), any())).thenAnswer(invocation -> {
            params.put(invocation.getArgument(0), invocation.getArgument(1));
            return statement;
        });
        when(statement.update()).thenReturn(1);
        VisibilityRecorder recorder = new VisibilityRecorder(jdbcClient, "sal", "https://www.autopartco.co");

        assertThat(recorder.record(visit("SERVICE_VIEW", service))).isTrue();

        assertThat(params).containsEntry("kind", "SERVICE_VIEW").containsEntry("serviceId", service);
        assertThat(params.get("productId")).isNull();
        assertThat(params.get("branchId")).isNull();
        // Un servicio ajeno o inactivo no inserta nada: lo decide el EXISTS sobre store_services del almacén.
        assertThat(VisibilityRecorder.INSERT_SQL).contains("v.id = CAST(:serviceId AS uuid) AND v.store_id = s.id "
                + "AND v.active");
    }

    @Test
    void anotherStoresServiceInsertsNothing() {
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
        when(jdbcClient.sql(VisibilityRecorder.INSERT_SQL)).thenReturn(statement);
        when(statement.param(anyString(), any())).thenReturn(statement);
        when(statement.update()).thenReturn(0);
        VisibilityRecorder recorder = new VisibilityRecorder(jdbcClient, "sal", "https://www.autopartco.co");

        assertThat(recorder.record(visit("SERVICE_VIEW", UUID.randomUUID()))).isFalse();
    }

    // --- Reporte -----------------------------------------------------------------------------------------------------

    @Test
    void reportIncludesServiceViewsTopServicesAndBookings() {
        rows(VisibilityQueries.TOTALS_SQL, Map.of("kind", "SERVICE_VIEW", "views", 9L, "visitors", 7L));
        rows(VisibilityQueries.BY_SOURCE_SQL);
        rows(VisibilityQueries.TOP_PRODUCTS_SQL);
        rows(VisibilityQueries.DAILY_SQL);
        rows(VisibilityQueries.BY_BRANCH_SQL);
        rows(VisibilityQueries.TOP_SERVICES_SQL, Map.of("service_id", service, "name", "Alineación", "views", 9L,
                "whatsapp_clicks", 3L, "directions_clicks", 1L, "bookings", 2L));
        rows(VisibilityQueries.BOOKINGS_SQL, Map.of("requested", 5L, "confirmed", 3L, "completed", 1L,
                "no_show", 1L));

        VisibilityReport report = new VisibilityQueries(jdbcClient).report(store, range);

        assertThat(report.serviceViews()).isEqualTo(9);
        assertThat(report.topServices())
                .containsExactly(new VisibilityReport.ServiceRow(service, "Alineación", 9, 3, 1, 2));
        assertThat(report.bookings()).isEqualTo(new VisibilityReport.Bookings(5, 3, 1, 1));
        // Las vistas de servicio no suman a las visitas del perfil ni de publicaciones.
        assertThat(report.profileViews()).isZero();
        assertThat(report.productViews()).isZero();
    }

    @Test
    void servicesAndBookingsAreScopedToTheStoreAndRange() {
        assertThat(VisibilityQueries.TOP_SERVICES_SQL)
                .contains("WHERE v.store_id = :storeId", "e.store_id = :storeId AND e.day BETWEEN :from AND :to",
                        "b.store_id = :storeId", "AT TIME ZONE 'America/Bogota'");
        assertThat(VisibilityQueries.BOOKINGS_SQL).contains("b.store_id = :storeId", "BETWEEN :from AND :to");
        assertThat(VisibilityQueries.TOP_SERVICES_SQL + VisibilityQueries.BOOKINGS_SQL)
                .doesNotContain("account", "contact_phone");
    }

    private VisibilityRecorder.Visit visit(String kind, UUID serviceId) {
        return new VisibilityRecorder.Visit("anon-1", kind, store, null, null, null, null, null, null, serviceId);
    }

    @SafeVarargs
    private void rows(String sql, Map<String, Object>... rows) {
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
        when(jdbcClient.sql(sql)).thenReturn(statement);
        when(statement.param(anyString(), any())).thenReturn(statement);
        when(statement.query(any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(0);
            List<Object> mapped = new ArrayList<>();
            int index = 0;
            for (Map<String, Object> row : rows) {
                ResultSet resultSet = mock(ResultSet.class,
                        answer -> answer.getArguments().length == 0 ? null : row.get(answer.<String>getArgument(0)));
                mapped.add(mapper.mapRow(resultSet, index++));
            }
            JdbcClient.MappedQuerySpec<Object> result = mock(JdbcClient.MappedQuerySpec.class);
            when(result.list()).thenReturn(mapped);
            return result;
        });
    }
}
