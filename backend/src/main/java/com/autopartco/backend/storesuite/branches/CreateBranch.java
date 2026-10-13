package com.autopartco.backend.storesuite.branches;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.BranchCreateRequest;
import com.autopartco.backend.core.organization.BranchResponse;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.core.billing.PlanEntitlements;

import lombok.AllArgsConstructor;

/**
 * Crea una sucursal. Requiere {@code BRANCH_CREATE} y cupo de sucursales en el plan. Bloquea la fila del almacén
 * antes de contar el cupo para que dos altas simultáneas no superen el límite.
 */
@Service
@AllArgsConstructor
public class CreateBranch {
    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final PlanEntitlements planEntitlements;
    private final BranchService branchService;

    @Transactional
    public BranchResponse execute(UUID storeId, String email, BranchCreateRequest request) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.BRANCH_CREATE);
        storeService.lockForUpdate(storeId);
        planEntitlements.requireCapacity(storeId, PlanEntitlements.Capacity.BRANCHES, 1);
        return branchService.create(storeId, request);
    }
}
