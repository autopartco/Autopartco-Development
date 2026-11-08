package com.autopartco.backend.storesuite.dashboard;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.billing.PlanEntitlements.Usage;
import com.autopartco.backend.core.billing.SubscriptionAccess;
import com.autopartco.backend.core.billing.SubscriptionView;
import com.autopartco.backend.core.billing.Subscriptions;
import com.autopartco.backend.core.inventory.StockService;
import com.autopartco.backend.core.order.SaleQueries;
import com.autopartco.backend.core.order.SalesSummary;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StoreApplicationSummary;
import com.autopartco.backend.core.organization.StoreApplications;
import com.autopartco.backend.core.organization.StorePermissionCodes;

import lombok.AllArgsConstructor;

/**
 * Panel del almacén: plan, uso y resumen de ventas. Requiere {@code STORE_READ}. Con {@code branchId}, ventas del mes
 * e inventario son de esa sucursal (si no es del almacén, 404 {@code BRANCH_NOT_FOUND}); plan, uso y suscripción
 * siguen siendo del almacén.
 */
@Service
@AllArgsConstructor
public class StoreDashboardQuery {
    private final StoreAccess storeAccess;
    private final PlanEntitlements entitlements;
    private final StoreApplications storeApplications;
    private final SaleQueries saleQueries;
    private final Subscriptions subscriptions;
    private final StockService stockService;
    private final BranchService branchService;

    @Transactional(readOnly = true)
    public DashboardResponse execute(UUID storeId, String email) {
        return execute(storeId, email, null);
    }

    @Transactional(readOnly = true)
    public DashboardResponse execute(UUID storeId, String email, UUID branchId) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_READ);
        if (branchId != null) {
            branchService.requireInStore(storeId, branchId);
        }
        SubscriptionAccess access = entitlements.requireAccess(storeId);
        StoreApplicationSummary application = storeApplications.summaryOf(storeId);
        Usage usage = entitlements.usage(storeId);
        SalesSummary sales = branchId == null
                ? saleQueries.monthlySummary(storeId)
                : saleQueries.monthlySummary(storeId, branchId);
        SubscriptionView subscription = subscriptions.view(storeId, LocalDateTime.now());
        StockService.StockSummary stock = branchId == null
                ? stockService.summaryForStore(storeId)
                : stockService.summaryForStore(storeId, branchId);

        return new DashboardResponse(
                storeId,
                application.businessName(),
                application.storeStatus(),
                access.plan(),
                access.selectedPlan(),
                application.configurationPending(),
                access.subscriptionExpired(),
                access.trialEndsAt(),
                access.paidThrough(),
                access.plan().maxBranches(),
                access.plan().maxProducts(),
                access.plan().maxUsers(),
                access.capabilities(),
                new DashboardResponse.Usage(usage.branches(), usage.products(), usage.users()),
                sales,
                subscription.status(),
                subscription.daysRemaining(),
                new DashboardResponse.Inventory(stock.listings(), stock.outOfStock(), stock.lowStock()),
                branchId
        );
    }
}
