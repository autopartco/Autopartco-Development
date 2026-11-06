package com.autopartco.backend.storesuite.analytics;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.analytics.ProductAssociations;

import lombok.AllArgsConstructor;

/**
 * Productos que se compran juntos en el almacén (pares con soporte, confianza y lift, y combo sugerido). Los
 * calcula la tarea nocturna de {@code core.analytics}.
 */
@Service
@AllArgsConstructor
public class GetProductAssociations {
    private final StoreAnalyticsAccess access;
    private final ProductAssociations productAssociations;

    @Transactional(readOnly = true)
    public ProductAssociations.StoreReport execute(UUID storeId, String email) {
        access.require(storeId, email);
        return productAssociations.storeReport(storeId);
    }
}
