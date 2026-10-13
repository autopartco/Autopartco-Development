package com.autopartco.backend.seller.dto;

import java.util.UUID;

import com.autopartco.backend.core.organization.StoreApplicationStatus;

public record SellerApplicationCreatedResponse(
        UUID applicationId,
        UUID storeId,
        StoreApplicationStatus status,
        String planCode
) {
}
