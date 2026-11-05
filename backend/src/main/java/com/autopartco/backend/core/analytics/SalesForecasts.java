package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

/**
 * Pronóstico de ventas del almacén ({@link SalesForecast}). Serie semanal (lunes, America/Bogota) de ingresos desde
 * la semana de la primera venta hasta la última semana completa (la anterior a la actual), con ceros.
 */
@Service
@AllArgsConstructor
public class SalesForecasts {
    public static final int WEEKS_REQUIRED = 8;
    static final int ACTUAL_WEEKS_SHOWN = 26;
    static final int PRODUCT_HISTORY_WEEKS = 26;
    static final int PRICE_WINDOW_DAYS = 90;
    static final int TOP_PRODUCTS = 10;

    private final SalesAnalyticsQueries queries;

    @Transactional(readOnly = true)
    public SalesForecast forecast(UUID storeId, UUID branchId) {
        return forecast(storeId, branchId, Analytics.today());
    }

    SalesForecast forecast(UUID storeId, UUID branchId, LocalDate today) {
        queries.requireBranch(storeId, branchId);
        LocalDate currentWeek = Analytics.weekStart(today);
        List<SalesAnalyticsQueries.WeeklyRevenue> rows = queries.weeklyRevenue(storeId, branchId, currentWeek);
        if (rows.isEmpty()) {
            return new SalesForecast(SalesForecast.INSUFFICIENT_HISTORY, 0, WEEKS_REQUIRED, null, null, List.of(),
                    List.of());
        }
        LocalDate firstWeek = rows.getFirst().weekStart();
        int weeks = (int) ChronoUnit.WEEKS.between(firstWeek, currentWeek);
        double[] series = new double[weeks];
        for (SalesAnalyticsQueries.WeeklyRevenue row : rows) {
            int index = (int) ChronoUnit.WEEKS.between(firstWeek, row.weekStart());
            if (index >= 0 && index < weeks) {
                series[index] += row.revenue().doubleValue();
            }
        }
        List<SalesForecast.WeekPoint> weekly = new ArrayList<>(actualPoints(series, firstWeek));
        if (weeks < WEEKS_REQUIRED) {
            return new SalesForecast(SalesForecast.INSUFFICIENT_HISTORY, weeks, WEEKS_REQUIRED, null, null, weekly,
                    List.of());
        }

        YearMonth nextMonth = YearMonth.from(today).plusMonths(1);
        Map<LocalDate, Integer> horizons = weeksCovering(nextMonth, currentWeek);
        int horizon = horizons.values().stream().max(Integer::compare).orElse(1);
        Forecasting.Model model = Forecasting.fit(series, horizon);

        double expected = 0;
        double low = 0;
        double high = 0;
        for (Map.Entry<LocalDate, Integer> week : horizons.entrySet()) {
            int h = week.getValue();
            double point = Math.max(0, model.forecast(h));
            double weekLow = Math.max(0, point - model.halfWidth(h));
            double weekHigh = point + model.halfWidth(h);
            double share = daysInMonth(week.getKey(), nextMonth) / 7.0;
            expected += point * share;
            low += weekLow * share;
            high += weekHigh * share;
            weekly.add(new SalesForecast.WeekPoint(week.getKey(), null, Analytics.money(point),
                    Analytics.money(weekLow), Analytics.money(weekHigh)));
        }
        SalesForecast.NextMonth month = new SalesForecast.NextMonth(nextMonth.toString(), Analytics.money(expected),
                Analytics.money(low), Analytics.money(high));
        return new SalesForecast(SalesForecast.OK, weeks, WEEKS_REQUIRED, model.method(), month, weekly,
                topProducts(storeId, branchId, today, currentWeek, nextMonth));
    }

    private static List<SalesForecast.WeekPoint> actualPoints(double[] series, LocalDate firstWeek) {
        List<SalesForecast.WeekPoint> points = new ArrayList<>();
        for (int index = Math.max(0, series.length - ACTUAL_WEEKS_SHOWN); index < series.length; index++) {
            points.add(new SalesForecast.WeekPoint(firstWeek.plusWeeks(index), Analytics.money(series[index]), null,
                    null, null));
        }
        return points;
    }

    /** Lunes de las semanas que tocan {@code month} con su horizonte h (1 = semana actual). */
    static Map<LocalDate, Integer> weeksCovering(YearMonth month, LocalDate currentWeek) {
        Map<LocalDate, Integer> weeks = new LinkedHashMap<>();
        LocalDate last = Analytics.weekStart(month.atEndOfMonth());
        for (LocalDate week = Analytics.weekStart(month.atDay(1)); !week.isAfter(last); week = week.plusWeeks(1)) {
            weeks.put(week, (int) ChronoUnit.WEEKS.between(currentWeek, week) + 1);
        }
        return weeks;
    }

    /** Días de la semana que empieza en {@code weekStart} que caen en {@code month}. */
    static int daysInMonth(LocalDate weekStart, YearMonth month) {
        int days = 0;
        for (int offset = 0; offset < 7; offset++) {
            if (YearMonth.from(weekStart.plusDays(offset)).equals(month)) {
                days++;
            }
        }
        return days;
    }

    private List<SalesForecast.ProductForecast> topProducts(UUID storeId, UUID branchId, LocalDate today,
                                                            LocalDate currentWeek, YearMonth nextMonth) {
        LocalDate historyStart = currentWeek.minusWeeks(PRODUCT_HISTORY_WEEKS);
        LocalDate priceStart = today.minusDays(PRICE_WINDOW_DAYS - 1L);
        LocalDate from = historyStart.isBefore(priceStart) ? historyStart : priceStart;
        Map<UUID, ProductHistory> products = new HashMap<>();
        for (SalesAnalyticsQueries.ProductDailySales day
                : queries.productDaily(storeId, branchId, new DateRange(from, today))) {
            ProductHistory product = products.computeIfAbsent(day.productId(), id -> new ProductHistory(day.name()));
            int week = (int) ChronoUnit.WEEKS.between(historyStart, Analytics.weekStart(day.day()));
            if (!day.day().isBefore(historyStart) && week >= 0 && week < PRODUCT_HISTORY_WEEKS) {
                product.weekly[week] += day.units();
            }
            if (!day.day().isBefore(priceStart)) {
                product.units90 += day.units();
                product.revenue90 = product.revenue90.add(day.revenue());
            }
        }
        double monthWeeks = nextMonth.lengthOfMonth() / 7.0;
        List<SalesForecast.ProductForecast> forecasts = new ArrayList<>();
        products.forEach((productId, product) -> {
            double units = Forecasting.simpleExponentialSmoothing(product.weekly) * monthWeeks;
            if (units <= 0) {
                return;
            }
            BigDecimal price = product.units90 == 0 ? BigDecimal.ZERO
                    : product.revenue90.divide(BigDecimal.valueOf(product.units90), 6, RoundingMode.HALF_UP);
            BigDecimal expectedUnits = Analytics.decimal(units, 1);
            forecasts.add(new SalesForecast.ProductForecast(productId, product.name, expectedUnits,
                    Analytics.money(price.multiply(BigDecimal.valueOf(units)))));
        });
        forecasts.sort(Comparator.comparing(SalesForecast.ProductForecast::expectedRevenue).reversed()
                .thenComparing(SalesForecast.ProductForecast::expectedUnits, Comparator.reverseOrder()));
        return forecasts.size() > TOP_PRODUCTS ? List.copyOf(forecasts.subList(0, TOP_PRODUCTS)) : forecasts;
    }

    private static final class ProductHistory {
        private final String name;
        private final double[] weekly = new double[PRODUCT_HISTORY_WEEKS];
        private long units90;
        private BigDecimal revenue90 = BigDecimal.ZERO;

        private ProductHistory(String name) {
            this.name = name;
        }
    }
}
