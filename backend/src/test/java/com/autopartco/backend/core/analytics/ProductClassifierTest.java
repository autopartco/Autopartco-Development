package com.autopartco.backend.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.analytics.ProductReport.ProductAction;

class ProductClassifierTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    @Test
    void abcByCumulativeRevenueShare() {
        UUID a = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID none = UUID.randomUUID();
        Map<UUID, BigDecimal> revenue = new LinkedHashMap<>();
        revenue.put(a, new BigDecimal("70"));
        revenue.put(a2, new BigDecimal("15"));
        revenue.put(b, new BigDecimal("10"));
        revenue.put(c, new BigDecimal("5"));
        revenue.put(none, BigDecimal.ZERO);

        Map<UUID, String> classes = ProductClassifier.abc(revenue);

        // Acumulado anterior: 0 → A, 70 → A, 85 → B, 95 → C.
        assertThat(classes).containsEntry(a, "A").containsEntry(a2, "A").containsEntry(b, "B")
                .containsEntry(c, "C").containsEntry(none, "C");
    }

    @Test
    void singleSellingProductIsA() {
        UUID only = UUID.randomUUID();
        assertThat(ProductClassifier.abc(Map.of(only, BigDecimal.TEN))).containsEntry(only, "A");
    }

    @Test
    void xyzByCoefficientOfVariation() {
        assertThat(ProductClassifier.xyz(new long[] {5, 5, 5, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0}))
                .isEqualTo("Z");
        assertThat(ProductClassifier.xyz(new long[] {10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10}))
                .isEqualTo("X");
        // media 6, desviación 4 → CV 0,67.
        assertThat(ProductClassifier.xyz(new long[] {2, 10, 2, 10})).isEqualTo("Y");
        assertThat(ProductClassifier.xyz(new long[] {9, 9, 9, 0, 0})).isNull();
    }

    @Test
    void suggestedMinStockUsesServiceLevelByClass() {
        // ceil(2·7 + 1,65·1·√7) = ceil(18,365) = 19
        assertThat(ProductClassifier.suggestedMinStock(2, 1, "A")).isEqualTo(19);
        // ceil(2·7 + 1,28·1·√7) = ceil(17,387) = 18
        assertThat(ProductClassifier.suggestedMinStock(2, 1, "B")).isEqualTo(18);
        // ceil(1·7 + 0) = 7 (sin redondear hacia arriba por error de coma flotante)
        assertThat(ProductClassifier.suggestedMinStock(1, 0, "C")).isEqualTo(7);
        assertThat(ProductClassifier.suggestedMinStock(0, 0, "C")).isNull();
    }

    @Test
    void actions() {
        assertThat(ProductClassifier.action("C", 0, 5, null, 200)).isEqualTo(ProductAction.LIQUIDATE);
        assertThat(ProductClassifier.action("C", 0, 5, null, 180)).isEqualTo(ProductAction.NO_MOVEMENT);
        assertThat(ProductClassifier.action("C", 0, 0, null, 400)).isEqualTo(ProductAction.MAINTAIN);
        assertThat(ProductClassifier.action("A", 10, 5, 13.9, 10)).isEqualTo(ProductAction.PROTECT_STOCK);
        assertThat(ProductClassifier.action("A", 10, 50, 14.0, 10)).isEqualTo(ProductAction.MAINTAIN);
        assertThat(ProductClassifier.action("C", 1, 500, 120.1, 10)).isEqualTo(ProductAction.REVIEW_PRICE);
        assertThat(ProductClassifier.action("B", 1, 500, 500.0, 10)).isEqualTo(ProductAction.MAINTAIN);
    }

    @Test
    void reportCombinesStockAndSales() {
        SalesAnalyticsQueries queries = mock(SalesAnalyticsQueries.class);
        UUID storeId = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID idle = UUID.randomUUID();
        OffsetDateTime old = TODAY.minusDays(400).atStartOfDay(Analytics.ZONE).toOffsetDateTime();
        when(queries.productStock(eq(storeId), isNull())).thenReturn(List.of(
                new SalesAnalyticsQueries.ProductStock(seller, "Filtro", "F-1", old, 9, 3),
                new SalesAnalyticsQueries.ProductStock(idle, "Bujía", "B-1", old, 4, 5)));
        List<SalesAnalyticsQueries.ProductDailySales> days = new ArrayList<>();
        for (int i = 0; i < 90; i++) {
            days.add(new SalesAnalyticsQueries.ProductDailySales(seller, "Filtro", TODAY.minusDays(i), 1,
                    new BigDecimal("1000")));
        }
        when(queries.productDaily(eq(storeId), isNull(), any())).thenReturn(days);

        ProductReport report = new ProductPerformance(queries).report(storeId, null, TODAY);

        assertThat(report.windowDays()).isEqualTo(90);
        ProductReport.Product top = report.products().getFirst();
        assertThat(top.productId()).isEqualTo(seller);
        assertThat(top.units()).isEqualTo(90);
        assertThat(top.revenue()).isEqualByComparingTo("90000");
        assertThat(top.abcClass()).isEqualTo("A");
        assertThat(top.xyzClass()).isEqualTo("X");
        assertThat(top.avgDailyUnits()).isEqualByComparingTo("1");
        assertThat(top.coverageDays()).isEqualByComparingTo("9");
        assertThat(top.suggestedMinStock()).isEqualTo(7);
        assertThat(top.action()).isEqualTo(ProductAction.PROTECT_STOCK);

        ProductReport.Product second = report.products().get(1);
        assertThat(second.abcClass()).isEqualTo("C");
        assertThat(second.xyzClass()).isNull();
        assertThat(second.coverageDays()).isNull();
        assertThat(second.suggestedMinStock()).isNull();
        assertThat(second.currentMinStock()).isEqualTo(5);
        assertThat(second.action()).isEqualTo(ProductAction.LIQUIDATE);
    }
}
