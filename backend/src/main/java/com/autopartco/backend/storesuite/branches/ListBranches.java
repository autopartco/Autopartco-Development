package com.autopartco.backend.storesuite.branches;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.BranchResponse;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;

import lombok.AllArgsConstructor;

/** Sucursales del almacén. Requiere {@code BRANCH_READ}. */
@Service
@AllArgsConstructor
public class ListBranches {
    private final StoreAccess storeAccess;
    private final BranchService branchService;

    @Transactional(readOnly = true)
    public List<BranchResponse> execute(UUID storeId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.BRANCH_READ);
        return branchService.list(storeId);
    }
}
