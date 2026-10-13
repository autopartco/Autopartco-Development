package com.autopartco.backend.storesuite.dashboard;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.autopartco.backend.core.billing.PlanResponse;
import com.autopartco.backend.core.order.SalesSummary;

public record DashboardResponse(
        UUID storeId,
        String storeName,
        String storeStatus,
        PlanResponse plan,
        PlanResponse selectedPlan,
        boolean setupRequired,
        boolean subscriptionExpired,
        LocalDateTime trialEndsAt,
        LocalDateTime paidThrough,
        Integer maxBranches,
        Integer maxProducts,
        Integer maxUsers,
        List<String> capabilities,
        Usage usage,
        SalesSummary sales
) {
    public record Usage(
            long branches,
            long products,
            long users
    ) {
    }
}
