package com.autopartco.backend.storesuite.orders;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.order.SaleDetailResponse;
import com.autopartco.backend.core.order.SaleStatusRequest;
import com.autopartco.backend.core.order.SaleStatusService;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;

import lombok.AllArgsConstructor;

/** Cambia el estado de una venta del almacén. Requiere {@code ORDER_UPDATE}. */
@Service
@AllArgsConstructor
public class UpdateStoreOrderStatus {
    private final StoreAccess storeAccess;
    private final SaleStatusService saleStatusService;

    @Transactional
    public SaleDetailResponse execute(UUID storeId, UUID saleId, String email, SaleStatusRequest request) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.ORDER_UPDATE);
        return saleStatusService.updateStatus(storeId, saleId, request);
    }
}
