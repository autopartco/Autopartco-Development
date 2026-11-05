package com.autopartco.backend.storesuite.analytics;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.analytics.SalesForecast;
import com.autopartco.backend.core.analytics.SalesForecasts;

import lombok.AllArgsConstructor;

/** Pronóstico de ventas del almacén (semanal y del próximo mes). */
@Service
@AllArgsConstructor
public class GetSalesForecast {
    private final StoreAnalyticsAccess access;
    private final SalesForecasts salesForecasts;

    @Transactional(readOnly = true)
    public SalesForecast execute(UUID storeId, String email, UUID branchId) {
        access.require(storeId, email);
        return salesForecasts.forecast(storeId, branchId);
    }
}
