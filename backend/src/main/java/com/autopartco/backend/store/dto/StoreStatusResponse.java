package com.autopartco.backend.store.dto;

import java.util.UUID;

public record StoreStatusResponse(
        UUID storeId,
        String state
) {
}