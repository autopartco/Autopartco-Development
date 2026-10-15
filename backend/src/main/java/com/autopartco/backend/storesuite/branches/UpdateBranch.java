package com.autopartco.backend.storesuite.branches;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.BranchResponse;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.organization.BranchUpdateRequest;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;

import lombok.AllArgsConstructor;

/** Actualiza nombre, teléfono o ubicación de una sucursal. Requiere {@code BRANCH_UPDATE}. */
@Service
@AllArgsConstructor
public class UpdateBranch {
    private final StoreAccess storeAccess;
    private final BranchService branchService;

    @Transactional
    public BranchResponse execute(UUID storeId, UUID branchId, String email, BranchUpdateRequest request) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.BRANCH_UPDATE);
        return branchService.update(storeId, branchId, request);
    }
}
