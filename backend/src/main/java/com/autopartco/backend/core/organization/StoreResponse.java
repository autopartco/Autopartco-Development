package com.autopartco.backend.core.organization;

import java.util.UUID;

public record StoreResponse(
        UUID id,
        String businessName,
        String nit,
        String businessEmail,
        String state,
        BusinessType businessType
) {
    public StoreResponse(UUID id, String businessName, String nit, String businessEmail, String state) {
        this(id, businessName, nit, businessEmail, state, BusinessType.PARTS);
    }
}