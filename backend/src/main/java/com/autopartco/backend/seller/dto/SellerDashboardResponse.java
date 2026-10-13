package com.autopartco.backend.seller.dto;

import com.autopartco.backend.core.billing.PlanResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SellerDashboardResponse(
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

    public record SalesSummary(
            long ordersThisMonth,
            BigDecimal revenueThisMonth,
            long pendingOrders,
            long preparingOrders
    ) {
    }
}
