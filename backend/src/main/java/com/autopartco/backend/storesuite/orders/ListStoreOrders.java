package com.autopartco.backend.storesuite.orders;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.order.SaleQueries;
import com.autopartco.backend.core.order.SaleSummaryResponse;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;

import lombok.AllArgsConstructor;

/** Ventas del almacén. Requiere {@code ORDER_READ}. */
@Service
@AllArgsConstructor
public class ListStoreOrders {
    private final StoreAccess storeAccess;
    private final SaleQueries saleQueries;

    @Transactional(readOnly = true)
    public List<SaleSummaryResponse> execute(UUID storeId, String email, String status, int page, int size) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.ORDER_READ);
        return saleQueries.list(storeId, status, page, size);
    }
}
