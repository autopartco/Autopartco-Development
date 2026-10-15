package com.autopartco.backend.storesuite.orders;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.order.SaleDetailResponse;
import com.autopartco.backend.core.order.ShipmentService;
import com.autopartco.backend.core.order.ShipmentStatusRequest;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;

import lombok.AllArgsConstructor;

/** Cambia el estado del envío de una venta. Requiere {@code SHIPMENT_UPDATE}. */
@Service
@AllArgsConstructor
public class UpdateStoreShipmentStatus {
    private final StoreAccess storeAccess;
    private final ShipmentService shipmentService;

    @Transactional
    public SaleDetailResponse execute(UUID storeId, UUID saleId, String email, ShipmentStatusRequest request) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.SHIPMENT_UPDATE);
        return shipmentService.updateStatus(storeId, saleId, request);
    }
}
