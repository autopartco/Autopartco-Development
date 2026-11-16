package com.autopartco.backend.storesuite.analytics;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.autopartco.backend.core.ml.MlPredictions;

import lombok.AllArgsConstructor;

/** Predicciones de los modelos del almacén: demanda por categoría (28 días) y riesgo de agotamiento (14 días). */
@Service
@AllArgsConstructor
public class GetPredictions {
    private final StoreAnalyticsAccess access;
    private final MlPredictions predictions;

    public MlPredictions.DemandForecast demand(UUID storeId, String email, UUID branchId) {
        access.require(storeId, email);
        return predictions.demand(storeId, branchId);
    }

    public MlPredictions.StockoutRisk stockout(UUID storeId, String email) {
        access.require(storeId, email);
        return predictions.stockout(storeId);
    }
}
