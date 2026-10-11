package com.autopartco.backend.store.dto;

import java.util.UUID;

public record BranchResponse(
        UUID id,
        UUID storeId,
        String name,
        String numberPhone,
        String state,
        BranchLocationResponse location
) {
}