package com.autopartco.backend.storesuite.orders;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.order.SaleQueries;
import com.autopartco.backend.core.order.SaleSummaryResponse;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;

import lombok.AllArgsConstructor;

/** Ventas del almacén, opcionalmente de una sucursal. Requiere {@code ORDER_READ}. */
@Service
@AllArgsConstructor
public class ListStoreOrders {
    private final StoreAccess storeAccess;
    private final SaleQueries saleQueries;
    private final BranchService branchService;

    @Transactional(readOnly = true)
    public List<SaleSummaryResponse> execute(UUID storeId, String email, String status, int page, int size) {
        return execute(storeId, email, status, null, page, size);
    }

    /** {@code branchId} de otro almacén: 404 {@code BRANCH_NOT_FOUND}. */
    @Transactional(readOnly = true)
    public List<SaleSummaryResponse> execute(
            UUID storeId, String email, String status, UUID branchId, int page, int size) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.ORDER_READ);
        if (branchId != null) {
            branchService.requireInStore(storeId, branchId);
        }
        return saleQueries.list(storeId, status, branchId, page, size);
    }
}
