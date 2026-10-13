package com.autopartco.backend.seller.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.autopartco.backend.core.identity.LoginResponse;

public record SellerSetupResponse(
        UUID storeId,
        String status,
        LocalDateTime trialEndsAt,
        SellerPlanResponse recommendedPlan,
        LoginResponse authentication
) {
}
