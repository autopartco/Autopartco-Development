package com.autopartco.backend.core.organization;

import java.util.UUID;

public record StoreStatusResponse(
        UUID storeId,
        String state
) {
}