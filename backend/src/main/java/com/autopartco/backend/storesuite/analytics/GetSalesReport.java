package com.autopartco.backend.storesuite.analytics;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.analytics.Analytics;
import com.autopartco.backend.core.analytics.DateRange;
import com.autopartco.backend.core.analytics.Granularity;
import com.autopartco.backend.core.analytics.SalesReport;
import com.autopartco.backend.core.analytics.SalesReports;

import lombok.AllArgsConstructor;

/**
 * Reporte de ventas del almacén. Rango por defecto: últimos 30 días; máximo 366 días (si no, 400
 * {@code INVALID_RANGE}). Agrupación por defecto {@code DAY}.
 */
@Service
@AllArgsConstructor
public class GetSalesReport {
    private final StoreAnalyticsAccess access;
    private final SalesReports salesReports;

    @Transactional(readOnly = true)
    public SalesReport execute(UUID storeId, String email, String from, String to, String granularity,
                               UUID branchId) {
        access.require(storeId, email);
        DateRange range = DateRange.resolve(from, to, Analytics.today());
        return salesReports.report(storeId, branchId, range, Granularity.parse(granularity));
    }
}
