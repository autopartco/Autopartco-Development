package com.autopartco.backend.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.shared.error.BadRequestException;

class SalesReportsTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    @Test
    void defaultRangeIsLastThirtyDaysAndPreviousHasSameLength() {
        DateRange range = DateRange.resolve(null, null, TODAY);
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(range.to()).isEqualTo(TODAY);
        assertThat(range.previous()).isEqualTo(new DateRange(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 10, 6)));
    }

    @Test
    void invalidRanges() {
        assertThat(DateRange.resolve("2025-11-05", "2026-11-05", TODAY).days()).isEqualTo(366);
        for (String[] invalid : new String[][] {
                {"2026-11-06", "2026-11-05"}, {"2025-11-04", "2026-11-05"}, {"05/11/2026", null}}) {
            assertThatThrownBy(() -> DateRange.resolve(invalid[0], invalid[1], TODAY))
                    .isInstanceOf(BadRequestException.class)
                    .extracting("code").isEqualTo("INVALID_RANGE");
        }
        assertThatThrownBy(() -> Granularity.parse("YEAR"))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("INVALID_GRANULARITY");
    }

    @Test
    void weeklySeriesIncludesEmptyPeriods() {
        DateRange range = new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20));
        List<SalesReport.SeriesPoint> series = SalesReports.series(List.of(
                new SalesAnalyticsQueries.DailySales(LocalDate.of(2026, 10, 1), new BigDecimal("100"), 1, 2),
                new SalesAnalyticsQueries.DailySales(LocalDate.of(2026, 10, 4), new BigDecimal("50"), 1, 1),
                new SalesAnalyticsQueries.DailySales(LocalDate.of(2026, 10, 19), new BigDecimal("30"), 1, 3)),
                range, Granularity.WEEK);

        assertThat(series).extracting(SalesReport.SeriesPoint::periodStart).containsExactly(
                LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 19));
        assertThat(series.get(0).revenue()).isEqualByComparingTo("150");
        assertThat(series.get(0).orders()).isEqualTo(2);
        assertThat(series.get(1).revenue()).isEqualByComparingTo("0");
        assertThat(series.get(3).units()).isEqualTo(3);
        assertThat(SalesReports.series(List.of(), range, Granularity.DAY)).hasSize(20);
    }

    @Test
    void totalsComputeTicketAndCancellationRate() {
        SalesReport.Totals totals = SalesReports.totals(new SalesAnalyticsQueries.Totals(
                new BigDecimal("300000"), 3, 7, new BigDecimal("1000"), 2, 1));
        assertThat(totals.averageTicket()).isEqualByComparingTo("100000");
        assertThat(totals.cancellationRate()).isEqualByComparingTo("0.25");
        SalesReport.Totals empty = SalesReports.totals(new SalesAnalyticsQueries.Totals(
                BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, 0, 0));
        assertThat(empty.averageTicket()).isEqualByComparingTo("0");
        assertThat(empty.cancellationRate()).isEqualByComparingTo("0");
    }
}
