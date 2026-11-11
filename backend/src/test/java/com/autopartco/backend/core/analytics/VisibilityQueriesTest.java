package com.autopartco.backend.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

class VisibilityQueriesTest {
    private final JdbcClient jdbcClient = mock(JdbcClient.class);
    private final VisibilityQueries queries = new VisibilityQueries(jdbcClient);
    private final UUID store = UUID.randomUUID();
    private final UUID product = UUID.randomUUID();
    private final UUID branch = UUID.randomUUID();
    private final DateRange range = new DateRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2));

    /** Devuelve {@code rows} al ejecutar {@code sql}; cada fila se lee por nombre de columna con el mapper real. */
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

    private void emptyReport() {
        rows(VisibilityQueries.TOTALS_SQL);
        rows(VisibilityQueries.BY_SOURCE_SQL);
        rows(VisibilityQueries.TOP_PRODUCTS_SQL);
        rows(VisibilityQueries.DAILY_SQL);
        rows(VisibilityQueries.BY_BRANCH_SQL);
    }

    @Test
    void reportAddsWhatsappAndDirectionsClicksWithoutChangingTheExistingTotals() {
        emptyReport();
        rows(VisibilityQueries.TOTALS_SQL,
                Map.of("kind", "PROFILE_VIEW", "views", 10L, "visitors", 6L),
                Map.of("kind", "PRODUCT_VIEW", "views", 4L, "visitors", 3L),
                Map.of("kind", "SHARE", "views", 2L, "visitors", 2L),
                Map.of("kind", "WHATSAPP_CLICK", "views", 5L, "visitors", 4L),
                Map.of("kind", "DIRECTIONS_CLICK", "views", 3L, "visitors", 3L));

        VisibilityReport report = queries.report(store, range);

        assertThat(report.profileViews()).isEqualTo(10);
        assertThat(report.profileVisitors()).isEqualTo(6);
        assertThat(report.productViews()).isEqualTo(4);
        assertThat(report.shares()).isEqualTo(2);
        assertThat(report.whatsappClicks()).isEqualTo(5);
        assertThat(report.directionsClicks()).isEqualTo(3);
    }

    @Test
    void reportWithoutEventsIsAllZeros() {
        emptyReport();

        VisibilityReport report = queries.report(store, range);

        assertThat(report.whatsappClicks()).isZero();
        assertThat(report.directionsClicks()).isZero();
        assertThat(report.byBranch()).isEmpty();
    }

    @Test
    void reportBreaksClicksDownBySourceProductDayAndBranch() {
        emptyReport();
        rows(VisibilityQueries.BY_SOURCE_SQL,
                Map.of("source", "WHATSAPP", "views", 7L, "visitors", 5L, "shares", 1L, "whatsapp_clicks", 4L),
                Map.of("source", "NUEVA_FUENTE", "views", 1L, "visitors", 1L, "shares", 0L, "whatsapp_clicks", 0L));
        rows(VisibilityQueries.TOP_PRODUCTS_SQL,
                Map.of("product_id", product, "name", "Pastillas", "views", 3L, "visitors", 2L, "shares", 0L,
                        "whatsapp_clicks", 2L));
        rows(VisibilityQueries.DAILY_SQL,
                Map.of("day", LocalDate.of(2026, 11, 1), "profile_views", 2L, "product_views", 1L,
                        "whatsapp_clicks", 1L),
                Map.of("day", LocalDate.of(2026, 11, 2), "profile_views", 0L, "product_views", 0L,
                        "whatsapp_clicks", 0L));
        rows(VisibilityQueries.BY_BRANCH_SQL,
                Map.of("branch_id", branch, "name", "Sede Norte", "whatsapp_clicks", 4L, "directions_clicks", 2L));

        VisibilityReport report = queries.report(store, range);

        assertThat(report.bySource()).containsExactly(
                new VisibilityReport.SourceRow(TrafficSource.WHATSAPP, 7, 5, 1, 4),
                new VisibilityReport.SourceRow(TrafficSource.OTHER, 1, 1, 0, 0));
        assertThat(report.topProducts())
                .containsExactly(new VisibilityReport.ProductRow(product, "Pastillas", 3, 2, 0, 2));
        assertThat(report.daily()).extracting(VisibilityReport.DayRow::whatsappClicks).containsExactly(1L, 0L);
        assertThat(report.byBranch())
                .containsExactly(new VisibilityReport.BranchRow(branch, "Sede Norte", 4, 2));
    }

    @Test
    void visitsExcludeClicksAndTheBranchBreakdownOnlyJoinsOwnBranches() {
        assertThat(VisibilityQueries.BY_SOURCE_SQL).contains("IN ('PROFILE_VIEW', 'PRODUCT_VIEW')");
        assertThat(VisibilityQueries.BY_BRANCH_SQL)
                .contains("b.store_id = e.store_id")
                .contains("e.branch_id IS NOT NULL");
        assertThat(VisibilityQueries.BY_SOURCE_SQL + VisibilityQueries.TOP_PRODUCTS_SQL
                + VisibilityQueries.DAILY_SQL + VisibilityQueries.BY_BRANCH_SQL).doesNotContain("account");
    }
}
