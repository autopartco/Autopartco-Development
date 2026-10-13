package com.autopartco.backend.seller.dto;

import com.autopartco.backend.core.identity.RegisterResponse;

import com.autopartco.backend.core.organization.StoreResponse;
import com.autopartco.backend.core.organization.StoreApplicationStatus;
import java.util.UUID;

public record SellerRegistrationResponse(
        RegisterResponse account,
        StoreResponse store,
        UUID applicationId,
        StoreApplicationStatus applicationStatus,
        String planCode
) {
}
