package com.autopartco.backend.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

class ProductAssociationsTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T17:00:00Z"), ZoneOffset.UTC);
    private static final UUID STORE = UUID.randomUUID();
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    private final ProductAssociationQueries queries = mock(ProductAssociationQueries.class);
    private final ProductAssociations associations =
            new ProductAssociations(queries, TransactionOperations.withoutTransaction(), CLOCK);

    private static List<ProductAssociationQueries.BasketLine> lines(UUID store, int firstOrder, int count,
            UUID... products) {
        List<ProductAssociationQueries.BasketLine> result = new ArrayList<>();
        for (int order = firstOrder; order < firstOrder + count; order++) {
            for (UUID product : products) {
                result.add(new ProductAssociationQueries.BasketLine(new UUID(9, order), store, product));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<ProductAssociationQueries.Association> inserted(UUID store) {
        ArgumentCaptor<List<ProductAssociationQueries.Association>> captor = ArgumentCaptor.forClass(List.class);
        verify(queries).insert(eq(store), captor.capture());
        return captor.getValue();
    }

    @Test
    void storeWithVolumeGetsOrdersPairsAndNoFallback() {
        List<ProductAssociationQueries.BasketLine> all = new ArrayList<>();
        all.addAll(lines(STORE, 0, 25, A, B));
        when(queries.basketLines(any())).thenReturn(all);
        when(queries.scopeStores()).thenReturn(List.of(STORE));

        associations.recompute();

        List<ProductAssociationQueries.Association> rows = inserted(STORE);
        assertThat(rows).hasSize(2).allMatch(row -> row.source().equals("ORDERS"));
        ProductAssociationQueries.Association ab = rows.stream()
                .filter(row -> row.antecedent().equals(A)).findFirst().orElseThrow();
        assertThat(ab.support()).isEqualByComparingTo("1");
        assertThat(ab.confidence()).isEqualByComparingTo("1");
        assertThat(ab.lift()).isEqualByComparingTo("1");
        verify(queries).deleteScope(STORE);
        verify(queries, never()).compatible(any(), any(), anyInt(), any());
        // El ámbito global también se reescribe con las mismas canastas.
        verify(queries).deleteScope(null);
        verify(queries).insert(eq(null), any());
    }

    @Test
    void storeWithoutVolumeFallsBackToCompatibilityThenCategoryWithZeroMetrics() {
        when(queries.basketLines(any())).thenReturn(lines(STORE, 0, 5, A, B));
        when(queries.scopeStores()).thenReturn(List.of(STORE));
        when(queries.compatible(eq(STORE), isNull(), eq(10), any()))
                .thenReturn(List.of(new ProductAssociationQueries.Link(A, B)));
        when(queries.sameCategory(eq(STORE), isNull(), eq(10), any()))
                .thenReturn(List.of(new ProductAssociationQueries.Link(A, C)));

        associations.recompute();

        List<ProductAssociationQueries.Association> rows = inserted(STORE);
        assertThat(rows).extracting(ProductAssociationQueries.Association::source)
                .containsExactly("COMPATIBILITY", "CATEGORY");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.support()).isEqualByComparingTo("0");
            assertThat(row.confidence()).isEqualByComparingTo("0");
            assertThat(row.lift()).isEqualByComparingTo("0");
        });
    }

    @Test
    void oneFailingScopeDoesNotStopTheOthers() {
        UUID other = UUID.randomUUID();
        when(queries.basketLines(any())).thenReturn(List.of());
        when(queries.scopeStores()).thenReturn(List.of(STORE, other));
        when(queries.compatible(eq(STORE), isNull(), anyInt(), any())).thenThrow(new IllegalStateException("boom"));

        associations.recompute();

        verify(queries).deleteScope(other);
        verify(queries, never()).deleteScope(STORE);
    }

    @Test
    void relatedUsesStoredRowsInOrderWithoutDuplicates() {
        when(queries.productStore(A)).thenReturn(STORE);
        when(queries.related(eq(A), eq(STORE), anyInt())).thenReturn(List.of(
                new ProductAssociationQueries.RelatedRow(B, "ORDERS"),
                new ProductAssociationQueries.RelatedRow(B, "ORDERS"),
                new ProductAssociationQueries.RelatedRow(C, "CATEGORY")));

        List<ProductAssociations.Related> result = associations.related(A, 6);

        assertThat(result).containsExactly(
                new ProductAssociations.Related(B, "ORDERS"), new ProductAssociations.Related(C, "CATEGORY"));
        verify(queries, never()).compatible(any(), any(), anyInt(), any());
    }

    @Test
    void relatedComputesFallbackOnTheFlyWhenThereAreNoRows() {
        when(queries.productStore(A)).thenReturn(STORE);
        when(queries.related(eq(A), eq(STORE), anyInt())).thenReturn(List.of());
        when(queries.compatible(eq(STORE), eq(A), eq(6), any()))
                .thenReturn(List.of(new ProductAssociationQueries.Link(A, B)));
        when(queries.sameCategory(eq(STORE), eq(A), eq(6), any())).thenReturn(List.of(
                new ProductAssociationQueries.Link(A, B), new ProductAssociationQueries.Link(A, C)));

        List<ProductAssociations.Related> result = associations.related(A, 6);

        assertThat(result).containsExactly(
                new ProductAssociations.Related(B, "COMPATIBILITY"), new ProductAssociations.Related(C, "CATEGORY"));
    }

    @Test
    void relatedRespectsTheLimit() {
        when(queries.productStore(A)).thenReturn(STORE);
        List<ProductAssociationQueries.RelatedRow> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rows.add(new ProductAssociationQueries.RelatedRow(new UUID(5, i), "ORDERS"));
        }
        when(queries.related(eq(A), eq(STORE), anyInt())).thenReturn(rows);

        assertThat(associations.related(A, 6)).hasSize(6);
    }

    private static ProductAssociationQueries.StoredPair stored(UUID from, String fromName, UUID to, String toName,
            String confidence, String lift, String support, String source) {
        return new ProductAssociationQueries.StoredPair(from, fromName, to, toName, new BigDecimal(support),
                new BigDecimal(confidence), new BigDecimal(lift), source,
                OffsetDateTime.parse("2026-11-05T09:00:00Z"));
    }

    @Test
    void storeReportListsOrdersPairsWithOrderCountsAndSuggestsTheBestCombo() {
        when(queries.storeOrders(eq(STORE), any())).thenReturn(25L);
        when(queries.storePairs(STORE)).thenReturn(List.of(
                stored(A, "Pastillas", C, "Líquido", "0.25", "0.78125", "0.16", "ORDERS"),
                stored(A, "Pastillas", B, "Disco", "0.75", "1.171875", "0.48", "ORDERS"),
                stored(A, "Pastillas", C, "Aceite", "0", "0", "0", "CATEGORY")));

        ProductAssociations.StoreReport report = associations.storeReport(STORE);

        assertThat(report.source()).isEqualTo("ORDERS");
        assertThat(report.computedAt()).isEqualTo(OffsetDateTime.parse("2026-11-05T09:00:00Z"));
        assertThat(report.pairs()).hasSize(2);
        assertThat(report.pairs().get(0).consequent().name()).isEqualTo("Disco");
        assertThat(report.pairs().get(0).orders()).isEqualTo(12);
        assertThat(report.pairs().get(1).orders()).isEqualTo(4);
        assertThat(report.suggestedCombo().productIds()).containsExactly(A, B);
        assertThat(report.suggestedCombo().names()).containsExactly("Pastillas", "Disco");
        assertThat(report.suggestedCombo().reason()).contains("12 pedidos").contains("75%");
    }

    @Test
    void storeReportWithOnlyFallbackRowsHasNoComboAndZeroOrders() {
        when(queries.storePairs(STORE)).thenReturn(List.of(
                stored(A, "Pastillas", C, "Aceite", "0", "0", "0", "CATEGORY"),
                stored(A, "Pastillas", B, "Disco", "0", "0", "0", "COMPATIBILITY")));

        ProductAssociations.StoreReport report = associations.storeReport(STORE);

        assertThat(report.source()).isEqualTo("COMPATIBILITY");
        assertThat(report.pairs()).hasSize(1);
        assertThat(report.pairs().get(0).orders()).isZero();
        assertThat(report.suggestedCombo()).isNull();
        verify(queries, never()).storeOrders(any(), any());
    }

    @Test
    void storeReportWithoutRowsIsEmpty() {
        when(queries.storePairs(STORE)).thenReturn(List.of());

        ProductAssociations.StoreReport report = associations.storeReport(STORE);

        assertThat(report.source()).isNull();
        assertThat(report.computedAt()).isNull();
        assertThat(report.pairs()).isEmpty();
        assertThat(report.suggestedCombo()).isNull();
    }

    @Test
    void storeReportWithoutComboWhenNoOrdersPairReachesMinimumConfidence() {
        when(queries.storeOrders(eq(STORE), any())).thenReturn(100L);
        when(queries.storePairs(STORE)).thenReturn(List.of(
                stored(A, "Pastillas", B, "Disco", "0.10", "3", "0.02", "ORDERS")));

        assertThat(associations.storeReport(STORE).suggestedCombo()).isNull();
    }
}
