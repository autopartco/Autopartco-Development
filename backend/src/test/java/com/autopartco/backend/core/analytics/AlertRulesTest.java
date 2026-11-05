package com.autopartco.backend.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.analytics.AlertRules.Candidate;
import com.autopartco.backend.core.analytics.AlertRules.CustomerState;
import com.autopartco.backend.core.analytics.AlertRules.DaySales;
import com.autopartco.backend.core.analytics.AlertRules.ProductState;
import com.autopartco.backend.core.analytics.AlertRules.Severity;
import com.autopartco.backend.core.analytics.AlertRules.Snapshot;
import com.autopartco.backend.core.analytics.AlertRules.Thresholds;
import com.autopartco.backend.core.analytics.AlertRules.Type;

class AlertRulesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final UUID STORE = UUID.randomUUID();
    private static final Thresholds DEFAULTS = Thresholds.defaults();

    // ---- SALES_DROP -------------------------------------------------------------------------------------------

    /** Un día de ventas por cada uno de los últimos {@code historyDays} días; los 7 recientes con {@code recent}. */
    private static List<DaySales> daily(int historyDays, int base, int recent) {
        List<DaySales> days = new ArrayList<>();
        IntStream.rangeClosed(1, historyDays).forEach(offset -> days.add(new DaySales(TODAY.minusDays(offset),
                BigDecimal.valueOf(offset <= 7 ? recent : base))));
        return days;
    }

    private static Snapshot sales(List<DaySales> daily) {
        return new Snapshot(STORE, TODAY, daily, List.of(), List.of());
    }

    @Test
    void salesDropWarnsBelowSixtyFivePercentOfWeeklyAverage() {
        List<Candidate> alerts = AlertRules.evaluate(sales(daily(80, 100, 50)), DEFAULTS);

        assertThat(alerts).singleElement().satisfies(alert -> {
            assertThat(alert.type()).isEqualTo(Type.SALES_DROP);
            assertThat(alert.severity()).isEqualTo(Severity.WARNING);
            assertThat(alert.message()).contains("50 %").contains("$350").contains("$700");
            assertThat(alert.payload()).containsEntry("ratio", new BigDecimal("0.50"));
            assertThat(alert.dedupeKey()).isEqualTo("SALES_DROP:" + STORE + ":2026-W45");
        });
    }

    @Test
    void salesDropIsCriticalBelowFortyPercent() {
        List<Candidate> alerts = AlertRules.evaluate(sales(daily(80, 100, 30)), DEFAULTS);

        assertThat(alerts).singleElement().extracting(Candidate::severity).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void salesDropDoesNotFireAtOrAboveThreshold() {
        // 65 % exacto: 455 de 700.
        assertThat(AlertRules.evaluate(sales(daily(80, 100, 65)), DEFAULTS)).isEmpty();
        assertThat(AlertRules.evaluate(sales(daily(80, 100, 90)), DEFAULTS)).isEmpty();
    }

    @Test
    void salesDropNeedsAtLeastEightWeeksOfHistory() {
        // Primera venta hace 62 días: falta una semana para tener 8 de línea base antes de la ventana.
        assertThat(AlertRules.evaluate(sales(daily(62, 100, 10)), DEFAULTS)).isEmpty();
        // Con 63 días de historial ya aplica.
        assertThat(AlertRules.evaluate(sales(daily(63, 100, 10)), DEFAULTS)).hasSize(1);
        assertThat(AlertRules.evaluate(sales(List.of()), DEFAULTS)).isEmpty();
    }

    @Test
    void salesDropRespectsCustomRatio() {
        Thresholds strict = new Thresholds(new BigDecimal("0.90"), 7, 90);

        assertThat(AlertRules.evaluate(sales(daily(80, 100, 85)), DEFAULTS)).isEmpty();
        assertThat(AlertRules.evaluate(sales(daily(80, 100, 85)), strict)).hasSize(1);
    }

    // ---- STOCKOUT_SOON / STOCKOUT ----------------------------------------------------------------------------

    private static ProductState product(String abc, Double coverage, long stock, long units30) {
        return new ProductState(UUID.randomUUID(), "Pastillas de freno", abc, coverage, stock, units30, 3, 400);
    }

    private static Snapshot products(ProductState... products) {
        return new Snapshot(STORE, TODAY, List.of(), List.of(products), List.of());
    }

    @Test
    void stockoutSoonForClassAOrBWithLowCoverageAndStock() {
        ProductState a = product("A", 6.9, 10, 20);
        ProductState b = product("B", 2.0, 4, 10);

        List<Candidate> alerts = AlertRules.evaluate(products(a, b), DEFAULTS);

        assertThat(alerts).extracting(Candidate::type).containsOnly(Type.STOCKOUT_SOON);
        assertThat(alerts).hasSize(2).allSatisfy(alert -> assertThat(alert.severity()).isEqualTo(Severity.WARNING));
        assertThat(alerts.get(0).dedupeKey()).isEqualTo("STOCKOUT_SOON:" + a.productId() + ":2026-W45");
        assertThat(alerts.get(0).message()).contains("Pastillas de freno").contains("6,9");
    }

    @Test
    void stockoutSoonIgnoresClassCEnoughCoverageNoSalesAndNoStock() {
        assertThat(AlertRules.evaluate(products(
                product("C", 2.0, 4, 10),
                product("A", 7.0, 50, 10),
                product("A", null, 50, 0),
                product("A", 0.0, 0, 0)), DEFAULTS)).isEmpty();
    }

    @Test
    void stockoutSoonRespectsCustomCoverageDays() {
        Thresholds longer = new Thresholds(new BigDecimal("0.65"), 14, 90);

        assertThat(AlertRules.evaluate(products(product("A", 10.0, 50, 10)), DEFAULTS)).isEmpty();
        assertThat(AlertRules.evaluate(products(product("A", 10.0, 50, 10)), longer)).hasSize(1);
    }

    @Test
    void stockoutWhenPublishedWithoutStockAndRecentSales() {
        ProductState sold = product("A", null, 0, 5);

        List<Candidate> alerts = AlertRules.evaluate(products(sold), DEFAULTS);

        assertThat(alerts).singleElement().satisfies(alert -> {
            assertThat(alert.type()).isEqualTo(Type.STOCKOUT);
            assertThat(alert.severity()).isEqualTo(Severity.CRITICAL);
            assertThat(alert.dedupeKey()).isEqualTo("STOCKOUT:" + sold.productId() + ":2026-W45");
            assertThat(alert.payload()).containsEntry("productId", sold.productId());
        });
    }

    @Test
    void stockoutIgnoresProductsWithStockOrWithoutRecentSales() {
        assertThat(AlertRules.evaluate(products(
                product("C", null, 3, 5),
                product("C", null, 0, 0)), DEFAULTS)).isEmpty();
    }

    // ---- CUSTOMER_AT_RISK -------------------------------------------------------------------------------------

    private static Snapshot customers(CustomerState... customers) {
        return new Snapshot(STORE, TODAY, List.of(), List.of(), List.of(customers));
    }

    @Test
    void customerAtRiskOnlyWhenFrequentBecomesAtRisk() {
        UUID person = UUID.randomUUID();

        List<Candidate> alerts = AlertRules.evaluate(customers(
                new CustomerState(person, "Ana Pérez", CustomerSegment.AT_RISK, CustomerSegment.FREQUENT),
                new CustomerState(UUID.randomUUID(), "Luis", CustomerSegment.AT_RISK, CustomerSegment.OCCASIONAL),
                new CustomerState(UUID.randomUUID(), "Marta", CustomerSegment.AT_RISK, null),
                new CustomerState(UUID.randomUUID(), "Juan", CustomerSegment.FREQUENT, CustomerSegment.FREQUENT),
                new CustomerState(UUID.randomUUID(), "Eva", CustomerSegment.DORMANT, CustomerSegment.FREQUENT)),
                DEFAULTS);

        assertThat(alerts).singleElement().satisfies(alert -> {
            assertThat(alert.type()).isEqualTo(Type.CUSTOMER_AT_RISK);
            assertThat(alert.severity()).isEqualTo(Severity.INFO);
            assertThat(alert.message()).contains("Ana Pérez");
            assertThat(alert.dedupeKey()).isEqualTo("CUSTOMER_AT_RISK:" + person + ":2026-W45");
            // Sin datos de contacto ni nombre en el payload.
            assertThat(alert.payload().keySet()).containsExactlyInAnyOrder("personId", "previousSegment", "segment");
            assertThat(alert.payload().toString()).doesNotContain("Ana").doesNotContain("@");
        });
    }

    // ---- DEAD_STOCK -------------------------------------------------------------------------------------------

    private static ProductState dead(String name, long stock, Integer daysSinceSale, long ageDays) {
        return new ProductState(UUID.randomUUID(), name, "C", null, stock, 0, daysSinceSale, ageDays);
    }

    @Test
    void deadStockIsOneWeeklySummaryWithCountAndAtMostTwentyProducts() {
        List<ProductState> products = new ArrayList<>();
        IntStream.rangeClosed(1, 25).forEach(index -> products.add(dead("Producto " + index, index, null, 365)));
        products.add(dead("Vendido hace poco", 5, 89, 365));
        products.add(dead("Nuevo", 5, null, 10));
        products.add(dead("Sin stock", 0, null, 365));
        products.add(dead("Vendido hace 90 días", 7, 90, 365));

        List<Candidate> alerts = AlertRules.evaluate(
                new Snapshot(STORE, TODAY, List.of(), products, List.of()), DEFAULTS);

        assertThat(alerts).singleElement().satisfies(alert -> {
            assertThat(alert.type()).isEqualTo(Type.DEAD_STOCK);
            assertThat(alert.severity()).isEqualTo(Severity.INFO);
            assertThat(alert.dedupeKey()).isEqualTo("DEAD_STOCK:" + STORE + ":2026-W45");
            assertThat(alert.payload()).containsEntry("count", 26);
            assertThat((List<?>) alert.payload().get("products")).hasSize(AlertRules.DEAD_STOCK_PAYLOAD_LIMIT);
            assertThat(((Map<?, ?>) ((List<?>) alert.payload().get("products")).get(0)).get("stock")).isEqualTo(25L);
            assertThat(alert.message()).startsWith("26 productos");
        });
    }

    @Test
    void deadStockAbsentWhenEveryProductMoves() {
        assertThat(AlertRules.evaluate(new Snapshot(STORE, TODAY, List.of(),
                List.of(dead("Rota", 5, 10, 365), dead("Sin stock", 0, null, 365)), List.of()), DEFAULTS)).isEmpty();
    }

    @Test
    void deadStockRespectsCustomDays() {
        Thresholds thirty = new Thresholds(new BigDecimal("0.65"), 7, 30);
        Snapshot snapshot = new Snapshot(STORE, TODAY, List.of(), List.of(dead("Lenta", 5, 45, 365)), List.of());

        assertThat(AlertRules.evaluate(snapshot, DEFAULTS)).isEmpty();
        assertThat(AlertRules.evaluate(snapshot, thirty)).hasSize(1);
    }

    // ---- dedupe_key -------------------------------------------------------------------------------------------

    @Test
    void dedupeKeyChangesEachIsoWeekAndIsStableWithinIt() {
        assertThat(AlertRules.isoWeek(LocalDate.of(2026, 11, 2))).isEqualTo("2026-W45");
        assertThat(AlertRules.isoWeek(LocalDate.of(2026, 11, 8))).isEqualTo("2026-W45");
        assertThat(AlertRules.isoWeek(LocalDate.of(2026, 11, 9))).isEqualTo("2026-W46");
        // Año ISO distinto del calendario.
        assertThat(AlertRules.isoWeek(LocalDate.of(2027, 1, 1))).isEqualTo("2026-W53");
        assertThat(AlertRules.isoWeek(LocalDate.of(2026, 3, 1))).isEqualTo("2026-W09");

        ProductState sold = product("A", null, 0, 5);
        Candidate monday = AlertRules.evaluate(new Snapshot(STORE, LocalDate.of(2026, 11, 2), List.of(),
                List.of(sold), List.of()), DEFAULTS).get(0);
        Candidate sunday = AlertRules.evaluate(new Snapshot(STORE, LocalDate.of(2026, 11, 8), List.of(),
                List.of(sold), List.of()), DEFAULTS).get(0);
        Candidate next = AlertRules.evaluate(new Snapshot(STORE, LocalDate.of(2026, 11, 9), List.of(),
                List.of(sold), List.of()), DEFAULTS).get(0);

        assertThat(monday.dedupeKey()).isEqualTo(sunday.dedupeKey()).isNotEqualTo(next.dedupeKey());
    }
}
