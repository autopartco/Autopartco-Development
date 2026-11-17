package com.autopartco.backend.core.ml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

class MlScoringJobsTest {
    private static final ModelRegistry REGISTRY = new ModelRegistry();
    private static final LocalDate CUTOFF = LocalDate.of(2026, 11, 16);
    private static final UUID VERSION = UUID.randomUUID();
    private static final UUID RUN = UUID.randomUUID();

    private MlQueries queries;
    private MlScoringJobs jobs;

    @BeforeEach
    void setUp() {
        queries = mock(MlQueries.class);
        jobs = new MlScoringJobs(REGISTRY, queries, Clock.fixed(Instant.parse("2026-11-16T07:30:00Z"), ZoneOffset.UTC));
        when(queries.ensureVersion(any())).thenReturn(VERSION);
    }

    @Test
    @SuppressWarnings("unchecked")
    void demandWritesOnePredictionPerRowWithPoissonInterval() {
        when(queries.startRun(eq(VERSION), anyString(), anyString())).thenReturn(Optional.of(RUN));
        Map<String, Object> row = row(ModelRegistry.DEMAND, 2);
        row.put("store_id", UUID.randomUUID());
        row.put("branch_id", UUID.randomUUID());
        row.put("category_id", UUID.randomUUID());
        when(queries.features(anyString(), anyMap())).thenReturn(List.of(row));

        assertThat(jobs.scoreDemand(CUTOFF)).isEqualTo(1);

        ArgumentCaptor<List<MlQueries.Prediction>> written = ArgumentCaptor.forClass(List.class);
        verify(queries).insertPredictions(eq(VERSION), eq(RUN), written.capture());
        MlQueries.Prediction p = written.getValue().getFirst();
        assertThat(p.entityType()).isEqualTo("BRANCH");
        assertThat(p.horizonDays()).isEqualTo(28);
        assertThat(p.targetDate()).isEqualTo(CUTOFF.plusDays(27));
        assertThat(p.value()).isPositive();
        assertThat(p.lowerBound()).isLessThanOrEqualTo(p.value()).isLessThanOrEqualTo(p.upperBound() + 1);
        assertThat(p.explanationJson()).contains(row.get("category_id").toString());
        verify(queries).finishRun(RUN, true, 1, null);
    }

    @Test
    void sameDayRunIsSkipped() {
        when(queries.startRun(eq(VERSION), anyString(), anyString())).thenReturn(Optional.empty());
        assertThat(jobs.scoreStockout(CUTOFF)).isZero();
        verify(queries, never()).features(anyString(), anyMap());
    }

    @Test
    void failureMarksRunFailedAndKeepsPreviousPredictions() {
        when(queries.startRun(eq(VERSION), anyString(), anyString())).thenReturn(Optional.of(RUN));
        when(queries.features(anyString(), anyMap())).thenReturn(List.of(new HashMap<>()));
        assertThatThrownBy(() -> jobs.scoreStockout(CUTOFF)).isInstanceOf(IllegalArgumentException.class);
        verify(queries).finishRun(RUN, false, 0, "IllegalArgumentException");
        verify(queries, never()).insertPredictions(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void paymentsSkipAlreadyAssessedAndFlagOutliers() {
        UUID done = UUID.randomUUID();
        when(queries.assessedPayments(eq(VERSION), any())).thenReturn(Set.of(done));
        Map<String, Object> normal = paymentRow(UUID.randomUUID(), false);
        Map<String, Object> outlier = paymentRow(UUID.randomUUID(), true);
        when(queries.features(anyString(), anyMap())).thenReturn(List.of(paymentRow(done, false), normal, outlier));

        assertThat(jobs.scorePayments()).isEqualTo(2);

        ArgumentCaptor<List<MlQueries.Assessment>> written = ArgumentCaptor.forClass(List.class);
        verify(queries).insertAssessments(eq(VERSION), written.capture());
        assertThat(written.getValue()).extracting(MlQueries.Assessment::decision).containsExactly("ALLOW", "REVIEW");
        assertThat(written.getValue()).allSatisfy(a -> assertThat(a.riskScore()).isBetween(0.0, 1.0));
    }

    @Test
    void manualRescoreUsesItsOwnRunKeySoTheNightlyRunDoesNotBlockIt() {
        when(queries.startRun(eq(VERSION), anyString(), anyString())).thenReturn(Optional.of(RUN));
        when(queries.features(anyString(), anyMap())).thenReturn(List.of());

        MlScoringJobs.Rescore result = jobs.rescore(ModelRegistry.STOCKOUT);

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(queries).startRun(eq(VERSION), key.capture(), anyString());
        assertThat(key.getValue()).startsWith(ModelRegistry.STOCKOUT + ":").contains(":" + CUTOFF + ":manual:");
        assertThat(key.getValue().length()).isLessThanOrEqualTo(120);
        verify(queries).finishRun(RUN, true, 0, null);
    }

    @Test
    void manualRescoreReportsFailureWithoutThrowing() {
        when(queries.startRun(eq(VERSION), anyString(), anyString())).thenReturn(Optional.of(RUN));
        when(queries.features(anyString(), anyMap())).thenReturn(List.of(new HashMap<>()));

        assertThat(jobs.rescore(ModelRegistry.DEMAND)).isEqualTo(
                new MlScoringJobs.Rescore(ModelRegistry.DEMAND, "FAILED", 0));
        verify(queries).finishRun(RUN, false, 0, "IllegalArgumentException");
    }

    @Test
    void unknownModelIsNotFound() {
        assertThatThrownBy(() -> jobs.rescore("nope")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void manualRescoreIsRejectedWhileAnotherScoringRuns() {
        when(queries.startRun(eq(VERSION), anyString(), anyString())).thenReturn(Optional.of(RUN));
        AtomicReference<Throwable> concurrent = new AtomicReference<>();
        when(queries.features(anyString(), anyMap())).thenAnswer(invocation -> {
            CompletableFuture.runAsync(() -> jobs.rescore(ModelRegistry.DEMAND))
                    .exceptionally(failure -> {
                        concurrent.set(failure.getCause());
                        return null;
                    })
                    .join();
            return List.of();
        });

        jobs.scoreNightly();

        assertThat(concurrent.get()).isInstanceOf(ConflictException.class);
    }

    private static Map<String, Object> row(String key, long value) {
        Map<String, Object> row = new HashMap<>();
        REGISTRY.model(key).orElseThrow().features().forEach(f -> row.put(f, value));
        return row;
    }

    private static Map<String, Object> paymentRow(UUID paymentId, boolean outlier) {
        Map<String, Object> row = new HashMap<>();
        row.put("payment_id", paymentId);
        row.put("order_id", UUID.randomUUID());
        row.put("log_amount", outlier ? 18.5 : 12.0);
        row.put("amount_vs_history", outlier ? 25.0 : 1.0);
        row.put("attempt_number", outlier ? 5 : 1);
        row.put("hour_sin", outlier ? 0.707 : -0.5);
        row.put("hour_cos", outlier ? 0.707 : -0.866);
        row.put("payments_30d", outlier ? 12 : 1);
        row.put("declined_30d", outlier ? 6 : 0);
        row.put("payments_24h", outlier ? 9 : 0);
        row.put("orders_365d", outlier ? 0 : 6);
        row.put("units", outlier ? 40 : 3);
        row.put("lines", outlier ? 15 : 2);
        row.put("branches", outlier ? 5 : 1);
        row.put("shipping_ratio", outlier ? 0.6 : 0.05);
        row.put("discount_ratio", outlier ? 0.7 : 0.0);
        row.put("account_age_days", outlier ? 0.05 : 120.0);
        return row;
    }
}
