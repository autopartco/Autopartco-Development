package com.autopartco.backend.storesuite.orders;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.order.SaleDetailResponse;
import com.autopartco.backend.core.order.SaleQueries;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;

import lombok.AllArgsConstructor;

/** Detalle de una venta del almacén. Requiere {@code ORDER_READ}. */
@Service
@AllArgsConstructor
public class GetStoreOrder {
    private final StoreAccess storeAccess;
    private final SaleQueries saleQueries;

    @Transactional(readOnly = true)
    public SaleDetailResponse execute(UUID storeId, UUID saleId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.ORDER_READ);
        return saleQueries.detail(storeId, saleId);
    }
}
