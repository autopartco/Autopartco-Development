package com.autopartco.backend.storesuite.onboarding;

import java.util.UUID;

import com.autopartco.backend.core.organization.StoreApplicationStatus;

public record StoreApplicationCreatedResponse(
        UUID applicationId,
        UUID storeId,
        StoreApplicationStatus status,
        String planCode
) {
}
