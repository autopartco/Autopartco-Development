package com.autopartco.backend.seller.dto;

import java.util.UUID;

import com.autopartco.backend.seller.entity.SellerApplicationStatus;

public record SellerApplicationCreatedResponse(
        UUID applicationId,
        UUID storeId,
        SellerApplicationStatus status,
        String planCode
) {
}
