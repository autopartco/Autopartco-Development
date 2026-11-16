package com.autopartco.backend.core.ml;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

/** Lecturas del último lote puntuado. Sin lote, {@code modelVersion} es nulo y la lista está vacía. */
@Service
@AllArgsConstructor
public class MlPredictions {
    static final double STOCKOUT_MIN_PROBABILITY = 0.2;
    static final int STOCKOUT_LIMIT = 100;

    private final MlQueries queries;

    public record DemandItem(UUID branchId, String branchName, UUID categoryId, String categoryCode,
            BigDecimal expectedUnits, int low, int high) {
    }

    public record DemandForecast(String modelVersion, String algorithm, OffsetDateTime generatedAt, int horizonDays,
            List<DemandItem> items) {
    }

    public record StockoutItem(UUID inventoryId, UUID productId, String productName, String sku, UUID branchId,
            String branchName, BigDecimal probability, String level, int stock, BigDecimal coverDays) {
    }

    public record StockoutRisk(String modelVersion, String algorithm, OffsetDateTime generatedAt, int horizonDays,
            List<StockoutItem> items) {
    }

    @Transactional(readOnly = true)
    public DemandForecast demand(UUID storeId, UUID branchId) {
        return queries.latestRun(ModelRegistry.DEMAND)
                .map(run -> new DemandForecast(run.version(), run.algorithm(), run.finishedAt(), 28,
                        queries.demand(run.runId(), storeId, branchId).stream()
                                .map(r -> new DemandItem(r.branchId(), r.branchName(), r.categoryId(),
                                        r.categoryCode(), decimal(r.expectedUnits(), 1), (int) r.low(),
                                        (int) r.high()))
                                .toList()))
                .orElseGet(() -> new DemandForecast(null, null, null, 28, List.of()));
    }

    @Transactional(readOnly = true)
    public StockoutRisk stockout(UUID storeId) {
        return queries.latestRun(ModelRegistry.STOCKOUT)
                .map(run -> new StockoutRisk(run.version(), run.algorithm(), run.finishedAt(), 14,
                        queries.stockout(run.runId(), storeId, STOCKOUT_MIN_PROBABILITY, STOCKOUT_LIMIT).stream()
                                .map(r -> new StockoutItem(r.inventoryId(), r.productId(), r.productName(), r.sku(),
                                        r.branchId(), r.branchName(), decimal(r.probability(), 3), r.level(),
                                        r.stock(), decimal(r.coverDays(), 1)))
                                .toList()))
                .orElseGet(() -> new StockoutRisk(null, null, null, 14, List.of()));
    }

    private static BigDecimal decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
