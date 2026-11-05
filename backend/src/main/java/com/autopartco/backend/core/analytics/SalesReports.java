package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

/** Reporte de ventas de un almacén (totales, comparación, serie, sucursales, categorías y productos). */
@Service
@AllArgsConstructor
public class SalesReports {
    static final int TOP_PRODUCTS = 10;

    private final SalesAnalyticsQueries queries;

    /**
     * Reporte del rango. Sucursal opcional; si no es del almacén, 404 {@code BRANCH_NOT_FOUND}. La serie incluye
     * todos los periodos del rango (con ceros).
     */
    @Transactional(readOnly = true)
    public SalesReport report(UUID storeId, UUID branchId, DateRange range, Granularity granularity) {
        requireBranch(storeId, branchId);
        SalesReport.Totals totals = totals(queries.totals(storeId, branchId, range));
        SalesReport.Totals previous = totals(queries.totals(storeId, branchId, range.previous()));
        List<SalesReport.SeriesPoint> series = series(queries.daily(storeId, branchId, range), range, granularity);
        return new SalesReport(
                range.from(),
                range.to(),
                granularity,
                totals,
                previous,
                series,
                queries.byBranch(storeId, branchId, range),
                queries.byCategory(storeId, branchId, range),
                queries.topProducts(storeId, branchId, range, TOP_PRODUCTS)
        );
    }

    private void requireBranch(UUID storeId, UUID branchId) {
        queries.requireBranch(storeId, branchId);
    }

    static SalesReport.Totals totals(SalesAnalyticsQueries.Totals row) {
        long attempted = row.orders() + row.cancelledOrders();
        BigDecimal cancellationRate = attempted == 0
                ? BigDecimal.ZERO.setScale(4)
                : BigDecimal.valueOf(row.cancelledOrders()).divide(BigDecimal.valueOf(attempted), 4, RoundingMode.HALF_UP);
        return new SalesReport.Totals(
                Analytics.money(row.revenue()),
                row.orders(),
                row.units(),
                Analytics.ratio(row.revenue(), row.orders()),
                row.customers(),
                Analytics.money(row.discounts()),
                row.cancelledOrders(),
                cancellationRate
        );
    }

    /** Agrupa las ventas diarias por periodo; un punto por periodo del rango aunque no tenga ventas. */
    static List<SalesReport.SeriesPoint> series(
            List<SalesAnalyticsQueries.DailySales> daily,
            DateRange range,
            Granularity granularity
    ) {
        Map<LocalDate, Accumulator> buckets = new LinkedHashMap<>();
        for (LocalDate start = granularity.periodStart(range.from());
             !start.isAfter(range.to());
             start = granularity.next(start)) {
            buckets.put(start, new Accumulator());
        }
        for (SalesAnalyticsQueries.DailySales day : daily) {
            Accumulator bucket = buckets.get(granularity.periodStart(day.day()));
            if (bucket != null) {
                bucket.revenue = bucket.revenue.add(day.revenue());
                bucket.orders += day.orders();
                bucket.units += day.units();
            }
        }
        List<SalesReport.SeriesPoint> points = new ArrayList<>(buckets.size());
        buckets.forEach((start, bucket) -> points.add(new SalesReport.SeriesPoint(
                start, Analytics.money(bucket.revenue), bucket.orders, bucket.units)));
        return points;
    }

    private static final class Accumulator {
        private BigDecimal revenue = BigDecimal.ZERO;
        private long orders;
        private long units;
    }
}
