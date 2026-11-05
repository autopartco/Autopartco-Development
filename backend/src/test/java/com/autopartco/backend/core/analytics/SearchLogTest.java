package com.autopartco.backend.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class SearchLogTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 5);
    private static final Executor DIRECT = Runnable::run;

    @Test
    void normalizesTextWithoutAccentsAndCollapsedSpaces() {
        assertThat(SearchText.normalize("  Pastillas   de FRENO  Mazdá ")).isEqualTo("pastillas de freno mazda");
        assertThat(SearchText.normalize("Ñandú\tÁÉÍÓÚ")).isEqualTo("nandu aeiou");
        assertThat(SearchText.normalize("   ")).isNull();
        assertThat(SearchText.normalize("x".repeat(250))).hasSize(200);
        assertThat(SearchText.clean("  Pastillas   Mazdá ")).isEqualTo("Pastillas Mazdá");
    }

    @Test
    void sessionHashIsDailySha256() {
        String hash = SearchText.sessionHash("abc", DAY);
        // SHA-256("abc2026-11-05")
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}")
                .isEqualTo(SearchText.sessionHash(" abc ", DAY))
                .isNotEqualTo(SearchText.sessionHash("abc", DAY.plusDays(1)));
        assertThat(SearchText.sessionHash(null, DAY)).isNull();
        assertThat(SearchText.sessionHash(" ", DAY)).isNull();
    }

    @Test
    void rowKeepsOnlyValidMunicipalityAndNonNegativeCount() {
        SearchLog.Row row = SearchLog.toRow(new MarketplaceSearch(" Filtro ", null, null, null, null, null,
                "05001", -3, "s-1", true), DAY);
        assertThat(row.queryText()).isEqualTo("Filtro");
        assertThat(row.queryNormalized()).isEqualTo("filtro");
        assertThat(row.municipalityCode()).isEqualTo("05001");
        assertThat(row.resultsCount()).isZero();
        assertThat(row.sessionHash()).isEqualTo(SearchText.sessionHash("s-1", DAY));
        assertThat(SearchLog.toRow(new MarketplaceSearch("x", null, null, null, null, null, "123", 1, null, false),
                DAY).municipalityCode()).isNull();
    }

    @Test
    void searchWithoutCriteriaIsNotRecorded() {
        JdbcClient jdbc = mock(JdbcClient.class);
        new SearchLog(jdbc, DIRECT).record(new MarketplaceSearch("  ", null, null, null, null, null, null, 4,
                "s", false));
        verify(jdbc, never()).sql(anyString());
    }

    @Test
    void recordsSearchWithVehicle() {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class, invocation ->
                invocation.getMethod().getReturnType().isInstance(invocation.getMock()) ? invocation.getMock() : 1);
        when(jdbc.sql(SearchLog.INSERT_SQL)).thenReturn(spec);
        UUID makeId = UUID.randomUUID();

        new SearchLog(jdbc, DIRECT).record(new MarketplaceSearch(null, makeId, null, null, null, null, null, 0,
                null, false));

        verify(spec).param("makeId", makeId.toString());
        verify(spec).param("resultsCount", 0);
        verify(spec).update();
    }

    @Test
    void databaseFailureNeverPropagates() {
        JdbcClient jdbc = mock(JdbcClient.class);
        when(jdbc.sql(anyString())).thenThrow(new IllegalStateException("conexión caída"));
        assertThatCode(() -> new SearchLog(jdbc, DIRECT).record(new MarketplaceSearch("filtro", null, null, null,
                null, null, null, 1, "s", false))).doesNotThrowAnyException();
    }

    @Test
    void fullQueueNeverPropagates() {
        JdbcClient jdbc = mock(JdbcClient.class);
        Executor rejecting = command -> {
            throw new RejectedExecutionException("cola llena");
        };
        assertThatCode(() -> new SearchLog(jdbc, rejecting).record(new MarketplaceSearch("filtro", null, null, null,
                null, null, null, 1, "s", false))).doesNotThrowAnyException();
    }
}
