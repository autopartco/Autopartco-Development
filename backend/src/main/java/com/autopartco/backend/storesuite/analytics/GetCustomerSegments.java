package com.autopartco.backend.storesuite.analytics;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.analytics.CustomerReport;
import com.autopartco.backend.core.analytics.CustomerSegment;
import com.autopartco.backend.core.analytics.CustomerSegments;

import lombok.AllArgsConstructor;

/** Clientes del almacén segmentados; segmento opcional (desconocido: 400 {@code INVALID_SEGMENT}). */
@Service
@AllArgsConstructor
public class GetCustomerSegments {
    private final StoreAnalyticsAccess access;
    private final CustomerSegments customerSegments;

    @Transactional(readOnly = true)
    public CustomerReport execute(UUID storeId, String email, String segment) {
        access.require(storeId, email);
        return customerSegments.storeCustomers(storeId, CustomerSegment.parse(segment));
    }
}
