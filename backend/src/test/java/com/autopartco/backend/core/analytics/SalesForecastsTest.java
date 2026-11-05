package com.autopartco.backend.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SalesForecastsTest {
    /** Jueves; semana actual desde el lunes 2026-11-02; próximo mes diciembre de 2026. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final LocalDate CURRENT_WEEK = LocalDate.of(2026, 11, 2);

    private final SalesAnalyticsQueries queries = mock(SalesAnalyticsQueries.class);
    private final UUID storeId = UUID.randomUUID();

    @Test
    void shortHistoryIsInsufficient() {
        when(queries.weeklyRevenue(storeId, null, CURRENT_WEEK)).thenReturn(List.of(
                new SalesAnalyticsQueries.WeeklyRevenue(CURRENT_WEEK.minusWeeks(7), new BigDecimal("100")),
                new SalesAnalyticsQueries.WeeklyRevenue(CURRENT_WEEK.minusWeeks(2), new BigDecimal("50"))));

        SalesForecast forecast = new SalesForecasts(queries).forecast(storeId, null, TODAY);

        assertThat(forecast.status()).isEqualTo(SalesForecast.INSUFFICIENT_HISTORY);
        assertThat(forecast.weeksAvailable()).isEqualTo(7);
        assertThat(forecast.weeksRequired()).isEqualTo(8);
        assertThat(forecast.nextMonth()).isNull();
        assertThat(forecast.topProducts()).isEmpty();
        assertThat(forecast.weekly()).hasSize(7).allSatisfy(week -> assertThat(week.forecast()).isNull());
        assertThat(forecast.weekly().get(1).actual()).isEqualByComparingTo("0");
    }

    @Test
    void noSalesIsInsufficientWithZeroWeeks() {
        SalesForecast forecast = new SalesForecasts(queries).forecast(storeId, null, TODAY);
        assertThat(forecast.weeksAvailable()).isZero();
        assertThat(forecast.weekly()).isEmpty();
    }

    @Test
    void constantSeriesForecastsSameValueWithZeroWidthInterval() {
        List<SalesAnalyticsQueries.WeeklyRevenue> rows = new ArrayList<>();
        for (int i = 10; i >= 1; i--) {
            rows.add(new SalesAnalyticsQueries.WeeklyRevenue(CURRENT_WEEK.minusWeeks(i), new BigDecimal("700")));
        }
        when(queries.weeklyRevenue(storeId, null, CURRENT_WEEK)).thenReturn(rows);
        UUID productId = UUID.randomUUID();
        List<SalesAnalyticsQueries.ProductDailySales> days = new ArrayList<>();
        for (int i = 1; i <= 26; i++) {
            days.add(new SalesAnalyticsQueries.ProductDailySales(productId, "Pastillas",
                    CURRENT_WEEK.minusWeeks(i), 7, new BigDecimal("70000")));
        }
        when(queries.productDaily(eq(storeId), isNull(), any())).thenReturn(days);

        SalesForecast forecast = new SalesForecasts(queries).forecast(storeId, null, TODAY);

        assertThat(forecast.status()).isEqualTo(SalesForecast.OK);
        assertThat(forecast.method()).isEqualTo(Forecasting.HOLT_DAMPED);
        assertThat(forecast.weeksAvailable()).isEqualTo(10);
        // Diciembre de 2026: 31 días, todos con previsión 700/semana → 700·31/7 = 3100.
        assertThat(forecast.nextMonth().month()).isEqualTo("2026-12");
        assertThat(forecast.nextMonth().expected()).isEqualByComparingTo("3100.00");
        assertThat(forecast.nextMonth().low()).isEqualByComparingTo("3100.00");
        assertThat(forecast.nextMonth().high()).isEqualByComparingTo("3100.00");
        // 10 semanas reales + 5 semanas (lunes 30-nov a 28-dic) que tocan diciembre.
        assertThat(forecast.weekly()).hasSize(15);
        assertThat(forecast.weekly().get(10).weekStart()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(forecast.weekly().get(10).actual()).isNull();
        assertThat(forecast.weekly().get(10).forecast()).isEqualByComparingTo("700");
        assertThat(forecast.topProducts()).singleElement().satisfies(product -> {
            assertThat(product.expectedUnits()).isEqualByComparingTo("31.0");
            // Precio medio 90 días: 10000 por unidad.
            assertThat(product.expectedRevenue()).isEqualByComparingTo("310000.00");
        });
    }

    @Test
    void holtIntervalGrowsWithSquareRootOfHorizon() {
        double[] series = {100, 140, 90, 160, 120, 180, 130, 170, 150, 190};
        Forecasting.Model model = Forecasting.fit(series, 4);
        Forecasting.Holt holt = Forecasting.holt(series, 4);

        assertThat(model.method()).isEqualTo(Forecasting.HOLT_DAMPED);
        assertThat(model.rmse()).isPositive().isEqualTo(holt.rmse());
        assertThat(model.halfWidth(4)).isCloseTo(2 * model.halfWidth(1), within(1e-9));
        assertThat(model.halfWidth(1)).isCloseTo(1.96 * model.rmse(), within(1e-9));
    }

    @Test
    void holtOnLinearSeriesIsDampedBelowExtrapolation() {
        double[] series = new double[12];
        for (int t = 0; t < series.length; t++) {
            series[t] = 100 + 10 * t;
        }
        Forecasting.Model model = Forecasting.fit(series, 3);
        // Sigue la tendencia pero amortiguada: por debajo de la extrapolación lineal (210 + 10·h).
        assertThat(model.forecast(1)).isGreaterThan(200).isLessThan(220);
        assertThat(model.forecast(3)).isGreaterThan(model.forecast(1)).isLessThan(240);
        assertThat(model.forecast(3) - model.forecast(2)).isLessThan(model.forecast(2) - model.forecast(1));
    }

    @Test
    void holtKnownValues() {
        // l0=10, b0=10; t1: ŷ=19 (e=1), l=19,4, b=9,04; t2: ŷ=27,536 (e=2,464), l=28,5216, b=8,23456.
        Forecasting.Model model = Forecasting.fit(new double[] {10, 20, 30}, 2);
        assertThat(model.forecast(1)).isCloseTo(35.932704, within(1e-6));
        assertThat(model.forecast(2)).isCloseTo(28.5216 + (0.9 + 0.81) * 8.23456, within(1e-6));
        assertThat(model.rmse()).isCloseTo(Math.sqrt((1 + 2.464 * 2.464) / 2), within(1e-6));
    }

    @Test
    void seasonalModelFromFiftyTwoWeeks() {
        double[] series = new double[104];
        for (int t = 0; t < series.length; t++) {
            series[t] = t % 52 == 0 ? 500 : 100;
        }
        Forecasting.Model model = Forecasting.fit(series, 1);
        assertThat(model.method()).isEqualTo(Forecasting.HOLT_WINTERS_ADDITIVE);
        // La semana 104 corresponde al índice estacional 0 (pico).
        assertThat(model.forecast(1)).isGreaterThan(400);
    }

    @Test
    void monthWeeksAndProration() {
        Map<LocalDate, Integer> weeks = SalesForecasts.weeksCovering(YearMonth.of(2026, 12), CURRENT_WEEK);
        assertThat(weeks).containsEntry(LocalDate.of(2026, 11, 30), 5)
                .containsEntry(LocalDate.of(2026, 12, 28), 9)
                .hasSize(5);
        assertThat(SalesForecasts.daysInMonth(LocalDate.of(2026, 11, 30), YearMonth.of(2026, 12))).isEqualTo(6);
        assertThat(SalesForecasts.daysInMonth(LocalDate.of(2026, 12, 28), YearMonth.of(2026, 12))).isEqualTo(4);
    }

    @Test
    void simpleExponentialSmoothing() {
        // 10 → 0,3·20 + 0,7·10 = 13 → 0,3·0 + 0,7·13 = 9,1
        assertThat(Forecasting.simpleExponentialSmoothing(new double[] {10, 20, 0})).isCloseTo(9.1, within(1e-9));
    }
}
