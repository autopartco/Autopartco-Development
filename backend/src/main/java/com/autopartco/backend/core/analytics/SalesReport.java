package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reporte de ventas de un almacén en un rango. {@code orders} cuenta ventas válidas; {@code revenue} suma
 * {@code sales.total}; {@code cancellationRate} es la fracción (0 a 1, 4 decimales) de canceladas sobre válidas más
 * canceladas. {@code previous} es el periodo anterior de igual duración.
 */
public record SalesReport(
        LocalDate from,
        LocalDate to,
        Granularity granularity,
        Totals totals,
        Totals previous,
        List<SeriesPoint> series,
        List<BranchSales> byBranch,
        List<CategorySales> byCategory,
        List<ProductSales> topProducts
) {
    public record Totals(
            BigDecimal revenue,
            long orders,
            long units,
            BigDecimal averageTicket,
            long customers,
            BigDecimal discounts,
            long cancelledOrders,
            BigDecimal cancellationRate
    ) {
    }

    public record SeriesPoint(LocalDate periodStart, BigDecimal revenue, long orders, long units) {
    }

    public record BranchSales(UUID branchId, String branchName, BigDecimal revenue, long orders, long units) {
    }

    public record CategorySales(UUID categoryId, String categoryCode, BigDecimal revenue, long units) {
    }

    public record ProductSales(UUID productId, String name, String sku, BigDecimal revenue, long units) {
    }
}
