package com.autopartco.backend.seller.dto;

import com.autopartco.backend.core.identity.RegisterResponse;

import com.autopartco.backend.store.dto.StoreResponse;
import com.autopartco.backend.seller.entity.SellerApplicationStatus;
import java.util.UUID;

public record SellerRegistrationResponse(
        RegisterResponse account,
        StoreResponse store,
        UUID applicationId,
        SellerApplicationStatus applicationStatus,
        String planCode
) {
}
