package com.autopartco.backend.auth.dto;

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
