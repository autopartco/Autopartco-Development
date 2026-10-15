package com.autopartco.backend.storesuite.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.billing.PlanResponse;
import com.autopartco.backend.core.billing.SubscriptionAccess;
import com.autopartco.backend.core.billing.SubscriptionStatus;
import com.autopartco.backend.core.billing.SubscriptionView;
import com.autopartco.backend.core.billing.Subscriptions;
import com.autopartco.backend.core.inventory.StockService;
import com.autopartco.backend.core.order.SaleQueries;
import com.autopartco.backend.core.order.SalesSummary;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StoreApplicationSummary;
import com.autopartco.backend.core.organization.StoreApplications;
import com.autopartco.backend.core.organization.StorePermissionCodes;

@ExtendWith(MockitoExtension.class)
class StoreDashboardQueryTest {
    @Mock
    private StoreAccess storeAccess;

    @Mock
    private PlanEntitlements entitlements;

    @Mock
    private StoreApplications storeApplications;

    @Mock
    private SaleQueries saleQueries;

    @Mock
    private Subscriptions subscriptions;

    @Mock
    private StockService stockService;

    @InjectMocks
    private StoreDashboardQuery storeDashboardQuery;

    @Test
    void checksStorePermissionBeforeLoadingDashboardData() {
        UUID storeId = UUID.randomUUID();
        doThrow(new AccessDeniedException("forbidden"))
                .when(storeAccess)
                .requirePermission("user@example.com", storeId, StorePermissionCodes.STORE_READ);

        assertThrows(
                AccessDeniedException.class,
                () -> storeDashboardQuery.execute(storeId, "user@example.com")
        );
        verifyNoInteractions(saleQueries, entitlements, storeApplications, subscriptions, stockService);
    }

    @Test
    void addsSubscriptionStatusDaysRemainingAndInventoryCounts() {
        UUID storeId = UUID.randomUUID();
        PlanResponse plan = new PlanResponse(UUID.randomUUID(), "TRY", "Prueba", null, 0, "COP", List.of(), false,
                true, 1, 100, 2, List.of("CSV_IMPORT"));
        when(entitlements.requireAccess(storeId)).thenReturn(new SubscriptionAccess(
                storeId, plan, null, false, null, null, null));
        when(entitlements.usage(storeId)).thenReturn(new PlanEntitlements.Usage(1, 40, 1));
        when(storeApplications.summaryOf(storeId)).thenReturn(new StoreApplicationSummary("Repuestos", "ACTIVE", false));
        when(saleQueries.monthlySummary(storeId)).thenReturn(new SalesSummary(0, BigDecimal.ZERO, 0, 0));
        when(subscriptions.view(eq(storeId), any())).thenReturn(new SubscriptionView(
                storeId, SubscriptionStatus.TRIALING, plan, null, null, null, null, null, 21L));
        when(stockService.summaryForStore(storeId)).thenReturn(new StockService.StockSummary(120, 4, 9));

        DashboardResponse response = storeDashboardQuery.execute(storeId, "user@example.com");

        assertEquals(SubscriptionStatus.TRIALING, response.subscriptionStatus());
        assertEquals(21L, response.daysRemaining());
        assertEquals(new DashboardResponse.Inventory(120, 4, 9), response.inventory());
        assertEquals(40, response.usage().products());
        assertEquals(100, response.maxProducts());
    }
}
