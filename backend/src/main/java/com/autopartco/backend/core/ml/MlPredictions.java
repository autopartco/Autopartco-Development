package com.autopartco.backend.core.ml;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import lombok.AllArgsConstructor;

/**
 * Lecturas del último lote puntuado. Sin lote, {@code modelVersion} es nulo y la lista está vacía. El nivel de riesgo
 * de agotamiento se deriva al leer con los umbrales del modelo cargado ({@code thresholds} de {@code model.json}).
 */
@Service
@AllArgsConstructor
public class MlPredictions {
    static final int STOCKOUT_LIMIT = 100;

    private final MlQueries queries;
    private final ModelRegistry registry;

    public record DemandItem(UUID branchId, String branchName, UUID categoryId, String categoryCode,
            BigDecimal expectedUnits, int low, int high) {
    }

    public record DemandForecast(String modelVersion, String algorithm, OffsetDateTime generatedAt, int horizonDays,
            List<DemandItem> items) {
    }

    public record StockoutItem(UUID inventoryId, UUID productId, String productName, String sku, UUID branchId,
            String branchName, BigDecimal probability, String level, int stock, BigDecimal coverDays) {
    }

    /**
     * @param minProbability probabilidad mínima para aparecer en la lista (umbral de riesgo medio)
     * @param baseRate probabilidad media de agotarse en el horizonte, como referencia
     */
    public record StockoutRisk(String modelVersion, String algorithm, OffsetDateTime generatedAt, int horizonDays,
            BigDecimal minProbability, BigDecimal baseRate, List<StockoutItem> items) {
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
        JsonNode thresholds = registry.model(ModelRegistry.STOCKOUT)
                .map(model -> model.metadata().path("thresholds"))
                .orElse(MissingNode.getInstance());
        double high = thresholds.path("high").asDouble(0.10);
        double medium = thresholds.path("medium").asDouble(0.05);
        BigDecimal min = decimal(medium, 3);
        BigDecimal baseRate = thresholds.has("baseRate") ? decimal(thresholds.path("baseRate").asDouble(), 4) : null;
        return queries.latestRun(ModelRegistry.STOCKOUT)
                .map(run -> new StockoutRisk(run.version(), run.algorithm(), run.finishedAt(), 14, min, baseRate,
                        queries.stockout(run.runId(), storeId, medium, STOCKOUT_LIMIT).stream()
                                .map(r -> new StockoutItem(r.inventoryId(), r.productId(), r.productName(), r.sku(),
                                        r.branchId(), r.branchName(), decimal(r.probability(), 3),
                                        r.probability() >= high ? "HIGH" : "MEDIUM", r.stock(),
                                        decimal(r.coverDays(), 1)))
                                .toList()))
                .orElseGet(() -> new StockoutRisk(null, null, null, 14, min, baseRate, List.of()));
    }

    private static BigDecimal decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
