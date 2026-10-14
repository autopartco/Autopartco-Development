package com.autopartco.backend.storesuite.branches;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;

import lombok.AllArgsConstructor;

/** Desactiva una sucursal (estado {@code INACTIVE}). Requiere {@code BRANCH_DELETE}. */
@Service
@AllArgsConstructor
public class DeactivateBranch {
    private final StoreAccess storeAccess;
    private final BranchService branchService;

    @Transactional
    public void execute(UUID storeId, UUID branchId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.BRANCH_DELETE);
        branchService.deactivate(storeId, branchId);
    }
}
