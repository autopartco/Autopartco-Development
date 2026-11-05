package com.autopartco.backend.storesuite.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.analytics.CustomerSegments;
import com.autopartco.backend.core.analytics.ProductPerformance;
import com.autopartco.backend.core.analytics.SalesForecasts;
import com.autopartco.backend.core.analytics.SalesReports;
import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.shared.error.ConflictException;

class StoreAnalyticsAccessTest {
    private static final String EMAIL = "owner@example.com";

    private final UUID storeId = UUID.randomUUID();
    private final StoreAccess storeAccess = mock(StoreAccess.class);
    private final PlanEntitlements entitlements = mock(PlanEntitlements.class);
    private final StoreAnalyticsAccess access = new StoreAnalyticsAccess(storeAccess, entitlements);
    private final SalesReports salesReports = mock(SalesReports.class);
    private final CustomerSegments customerSegments = mock(CustomerSegments.class);
    private final ProductPerformance productPerformance = mock(ProductPerformance.class);
    private final SalesForecasts salesForecasts = mock(SalesForecasts.class);

    @Test
    void checksPermissionBeforeCapability() {
        new GetSalesForecast(access, salesForecasts).execute(storeId, EMAIL, null);

        InOrder order = inOrder(storeAccess, entitlements, salesForecasts);
        order.verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.ANALYTICS_READ);
        order.verify(entitlements).requireCapability(storeId, "ADVANCED_REPORTS");
        order.verify(salesForecasts).forecast(storeId, null);
    }

    @Test
    void withoutPermissionNothingIsRead() {
        doThrow(new AccessDeniedException("denied"))
                .when(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.ANALYTICS_READ);

        assertThatThrownBy(() -> new GetSalesReport(access, salesReports)
                .execute(storeId, EMAIL, null, null, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> new GetCustomerSegments(access, customerSegments).execute(storeId, EMAIL, null))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(entitlements, salesReports, customerSegments);
    }

    @Test
    void planWithoutAdvancedReportsIsRejected() {
        doThrow(new ConflictException("SELLER_PLAN_FEATURE_UNAVAILABLE", "No incluida", Map.of()))
                .when(entitlements).requireCapability(storeId, "ADVANCED_REPORTS");

        assertThatThrownBy(() -> new GetProductPerformance(access, productPerformance).execute(storeId, EMAIL, null))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("SELLER_PLAN_FEATURE_UNAVAILABLE");
        verifyNoInteractions(productPerformance);
    }

    @Test
    void reportDelegatesWithDefaultGranularity() {
        when(salesReports.report(any(), any(), any(), any())).thenReturn(null);
        new GetSalesReport(access, salesReports).execute(storeId, EMAIL, "2026-10-01", "2026-10-31", "week", null);
        org.mockito.Mockito.verify(salesReports).report(org.mockito.ArgumentMatchers.eq(storeId),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.argThat(range -> range.days() == 31),
                org.mockito.ArgumentMatchers.eq(com.autopartco.backend.core.analytics.Granularity.WEEK));
        assertThat(StoreAnalyticsAccess.CAPABILITY).isEqualTo("ADVANCED_REPORTS");
    }
}
