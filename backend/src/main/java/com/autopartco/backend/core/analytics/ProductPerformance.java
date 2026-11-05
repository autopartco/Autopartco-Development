package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

/**
 * Rendimiento de los productos vivos con listado del almacén ({@link ProductReport}). Ventana de
 * {@value #WINDOW_DAYS} días que termina hoy; la clase XYZ usa {@value #WEEKS} bloques de 7 días que terminan hoy.
 * Con sucursal: stock y stock mínimo de esa sucursal; sin ella, stock sumado y el mayor stock mínimo.
 */
@Service
@AllArgsConstructor
public class ProductPerformance {
    public static final int WINDOW_DAYS = 90;
    static final int WEEKS = 13;

    private final SalesAnalyticsQueries queries;

    @Transactional(readOnly = true)
    public ProductReport report(UUID storeId, UUID branchId) {
        return report(storeId, branchId, Analytics.today());
    }

    ProductReport report(UUID storeId, UUID branchId, LocalDate today) {
        queries.requireBranch(storeId, branchId);
        LocalDate windowStart = today.minusDays(WINDOW_DAYS - 1L);
        LocalDate weeksStart = today.minusDays(WEEKS * 7L - 1);
        List<SalesAnalyticsQueries.ProductStock> stock = queries.productStock(storeId, branchId);
        List<SalesAnalyticsQueries.ProductDailySales> sales =
                queries.productDaily(storeId, branchId, new DateRange(weeksStart, today));

        Map<UUID, Stats> stats = new HashMap<>();
        for (SalesAnalyticsQueries.ProductDailySales day : sales) {
            Stats product = stats.computeIfAbsent(day.productId(), id -> new Stats());
            int weekIndex = (int) (ChronoUnit.DAYS.between(day.day(), today) / 7);
            if (weekIndex >= 0 && weekIndex < WEEKS) {
                product.weekly[weekIndex] += day.units();
            }
            int dayIndex = (int) ChronoUnit.DAYS.between(windowStart, day.day());
            if (dayIndex >= 0 && dayIndex < WINDOW_DAYS) {
                product.daily[dayIndex] += day.units();
                product.units += day.units();
                product.revenue = product.revenue.add(day.revenue());
            }
        }

        Map<UUID, BigDecimal> revenue = new HashMap<>();
        for (SalesAnalyticsQueries.ProductStock product : stock) {
            Stats product90 = stats.get(product.productId());
            revenue.put(product.productId(), product90 == null ? BigDecimal.ZERO : product90.revenue);
        }
        Map<UUID, String> abc = ProductClassifier.abc(revenue);

        List<ProductReport.Product> products = new ArrayList<>(stock.size());
        for (SalesAnalyticsQueries.ProductStock product : stock) {
            Stats product90 = stats.getOrDefault(product.productId(), new Stats());
            String abcClass = abc.getOrDefault(product.productId(), ProductClassifier.C);
            double avgDaily = product90.units / (double) WINDOW_DAYS;
            double dailyStdDev = ProductClassifier.standardDeviation(product90.daily,
                    ProductClassifier.mean(product90.daily));
            Double coverage = ProductClassifier.coverageDays(product.stock(), avgDaily);
            long ageDays = product.createdAt() == null ? 0
                    : ChronoUnit.DAYS.between(product.createdAt().atZoneSameInstant(Analytics.ZONE).toLocalDate(), today);
            products.add(new ProductReport.Product(
                    product.productId(),
                    product.name(),
                    product.sku(),
                    Analytics.money(product90.revenue),
                    product90.units,
                    abcClass,
                    ProductClassifier.xyz(product90.weekly),
                    product.stock(),
                    Analytics.decimal(avgDaily, 2),
                    coverage == null ? null : Analytics.decimal(coverage, 1),
                    product.minStock(),
                    ProductClassifier.suggestedMinStock(avgDaily, dailyStdDev, abcClass),
                    ProductClassifier.action(abcClass, product90.units, product.stock(), coverage, ageDays)));
        }
        products.sort(Comparator.comparing(ProductReport.Product::revenue).reversed()
                .thenComparing(ProductReport.Product::name, Comparator.nullsLast(Comparator.naturalOrder())));
        return new ProductReport(WINDOW_DAYS, products);
    }

    private static final class Stats {
        private final long[] weekly = new long[WEEKS];
        private final long[] daily = new long[WINDOW_DAYS];
        private long units;
        private BigDecimal revenue = BigDecimal.ZERO;
    }
}
