package com.autopartco.backend.storesuite.dashboard;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

/** Panel del almacén. */
@RestController
@RequestMapping(ApiPaths.STORES + "/{storeId}/dashboard")
@AllArgsConstructor
public class DashboardController {
    private final StoreDashboardQuery storeDashboardQuery;

    @GetMapping
    /** {@code branchId} opcional: filtra ventas del mes e inventario; si no es del almacén, 404 {@code BRANCH_NOT_FOUND}. */
    public DashboardResponse dashboard(
            @PathVariable UUID storeId,
            @RequestParam(required = false) UUID branchId,
            Authentication authentication
    ) {
        return storeDashboardQuery.execute(storeId, authentication.getName(), branchId);
    }
}
