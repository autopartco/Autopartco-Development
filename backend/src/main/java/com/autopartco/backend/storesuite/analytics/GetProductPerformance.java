package com.autopartco.backend.storesuite.analytics;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.analytics.ProductPerformance;
import com.autopartco.backend.core.analytics.ProductReport;

import lombok.AllArgsConstructor;

/**
 * Rendimiento de los productos del almacén (ABC/XYZ, cobertura, stock mínimo sugerido y acción). El stock mínimo se
 * cambia con el endpoint existente {@code PATCH …/branches/{branchId}/products/{productId}/min-stock}.
 */
@Service
@AllArgsConstructor
public class GetProductPerformance {
    private final StoreAnalyticsAccess access;
    private final ProductPerformance productPerformance;

    @Transactional(readOnly = true)
    public ProductReport execute(UUID storeId, String email, UUID branchId) {
        access.require(storeId, email);
        return productPerformance.report(storeId, branchId);
    }
}
