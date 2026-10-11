package com.autopartco.backend.seller.dto;

import java.util.List;
import java.util.UUID;

public record SellerPlanResponse(
        UUID id,
        String code,
        String name,
        String description,
        long monthlyPriceCop,
        String currency,
        List<String> features,
        boolean recommended,
        boolean enabled,
        Integer maxBranches,
        Integer maxProducts,
        Integer maxUsers,
        List<String> capabilities
) {
}
