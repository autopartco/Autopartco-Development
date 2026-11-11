package com.autopartco.backend.core.advertising;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.advertising.AdStatsCalculator.DayTotals;
import com.autopartco.backend.core.advertising.AdStatsCalculator.Row;

class AdStatsTest {
    private static final LocalDate D1 = LocalDate.of(2026, 11, 10);
    private static final LocalDate D2 = LocalDate.of(2026, 11, 11);
    private static final UUID P1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID P2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static Row row(LocalDate day, AdPlacement placement, UUID product, AdEventKind kind, long events) {
        return new Row(day, placement, product, product.equals(P1) ? "Pastillas" : "Disco", kind, events, 0,
                BigDecimal.ZERO);
    }

    private static Row order(LocalDate day, AdPlacement placement, UUID product, long orders, String amount) {
        return new Row(day, placement, product, product.equals(P1) ? "Pastillas" : "Disco", AdEventKind.ORDER,
                orders, orders, new BigDecimal(amount));
    }

    @Test
    void totalsMatchFixedEvents() {
        List<Row> rows = List.of(
                row(D1, AdPlacement.SEARCH_TOP, P1, AdEventKind.IMPRESSION, 60),
                row(D1, AdPlacement.SEARCH_TOP, P2, AdEventKind.IMPRESSION, 40),
                row(D1, AdPlacement.SEARCH_TOP, P1, AdEventKind.CLICK, 6),
                row(D1, AdPlacement.SEARCH_TOP, P1, AdEventKind.WHATSAPP_CLICK, 2),
                row(D2, AdPlacement.HOME_FEATURED, P2, AdEventKind.IMPRESSION, 100),
                row(D2, AdPlacement.HOME_FEATURED, P2, AdEventKind.CLICK, 4),
                row(D2, AdPlacement.HOME_FEATURED, P2, AdEventKind.ADD_TO_CART, 3),
                order(D2, AdPlacement.SEARCH_TOP, P1, 1, "170000.00"),
                order(D2, AdPlacement.HOME_FEATURED, P2, 1, "30000.00"));
        // El pedido del día 2 tiene los dos productos: un solo pedido distinto ese día.
        List<DayTotals> days = List.of(new DayTotals(D1, 80, 0), new DayTotals(D2, 70, 1));

        AdStats stats = AdStatsCalculator.compute(D1, D2, rows, days, new BigDecimal("100000"));

        assertThat(stats.impressions()).isEqualTo(200);
        assertThat(stats.reach()).isEqualTo(150);
        assertThat(stats.clicks()).isEqualTo(10);
        assertThat(stats.ctr()).isEqualByComparingTo("0.0500");
        assertThat(stats.whatsappClicks()).isEqualTo(2);
        assertThat(stats.addToCart()).isEqualTo(3);
        assertThat(stats.orders()).isEqualTo(1);
        assertThat(stats.revenue()).isEqualByComparingTo("200000.00");
        assertThat(stats.cost()).isEqualByComparingTo("100000.00");
        assertThat(stats.costPerClick()).isEqualByComparingTo("10000.00");
        assertThat(stats.roas()).isEqualByComparingTo("2.0000");

        assertThat(stats.daily()).hasSize(2);
        AdStats.Daily first = stats.daily().getFirst();
        assertThat(first.day()).isEqualTo(D1);
        assertThat(first.impressions()).isEqualTo(100);
        assertThat(first.reach()).isEqualTo(80);
        assertThat(first.clicks()).isEqualTo(6);
        AdStats.Daily second = stats.daily().get(1);
        assertThat(second.orders()).isEqualTo(1);
        assertThat(second.revenue()).isEqualByComparingTo("200000.00");

        assertThat(stats.byPlacement()).extracting(AdStats.ByPlacement::placement)
                .containsExactly(AdPlacement.SEARCH_TOP, AdPlacement.HOME_FEATURED);
        AdStats.ByPlacement top = stats.byPlacement().getFirst();
        assertThat(top.impressions()).isEqualTo(100);
        assertThat(top.clicks()).isEqualTo(6);
        assertThat(top.ctr()).isEqualByComparingTo("0.0600");
        assertThat(top.revenue()).isEqualByComparingTo("170000.00");

        assertThat(stats.byProduct()).extracting(AdStats.ByProduct::productId).containsExactly(P2, P1);
        AdStats.ByProduct disco = stats.byProduct().getFirst();
        assertThat(disco.productName()).isEqualTo("Disco");
        assertThat(disco.impressions()).isEqualTo(140);
        assertThat(disco.clicks()).isEqualTo(4);
        assertThat(disco.addToCart()).isEqualTo(3);
        assertThat(disco.orders()).isEqualTo(1);
    }

    @Test
    void emptyRangeHasZerosAndOneEntryPerDay() {
        AdStats stats = AdStatsCalculator.compute(D1, D2, List.of(), List.of(), BigDecimal.ZERO);

        assertThat(stats.impressions()).isZero();
        assertThat(stats.ctr()).isEqualByComparingTo("0");
        assertThat(stats.costPerClick()).isEqualByComparingTo("0");
        assertThat(stats.roas()).isEqualByComparingTo("0");
        assertThat(stats.daily()).extracting(AdStats.Daily::day).containsExactly(D1, D2);
        assertThat(stats.byPlacement()).isEmpty();
    }

    @Test
    void costIsProratedToTheRequestedRangeAndZeroWhenUnpaid() {
        LocalDate start = LocalDate.of(2026, 11, 1);
        LocalDate end = LocalDate.of(2026, 11, 10);
        BigDecimal total = new BigDecimal("150000.00");

        assertThat(AdStatsCalculator.proratedCost(total, true, start, end, start, end))
                .isEqualByComparingTo("150000.00");
        assertThat(AdStatsCalculator.proratedCost(total, true, start, end, LocalDate.of(2026, 11, 9),
                LocalDate.of(2026, 11, 20))).isEqualByComparingTo("30000.00");
        assertThat(AdStatsCalculator.proratedCost(total, true, start, end, LocalDate.of(2026, 11, 11),
                LocalDate.of(2026, 11, 20))).isEqualByComparingTo("0");
        assertThat(AdStatsCalculator.proratedCost(total, false, start, end, start, end)).isEqualByComparingTo("0");
    }
}
