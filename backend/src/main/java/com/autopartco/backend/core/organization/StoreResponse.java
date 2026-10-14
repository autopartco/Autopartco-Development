package com.autopartco.backend.core.organization;

import java.util.UUID;

public record StoreResponse(
        UUID id,
        String businessName,
        String nit,
        String businessEmail,
        String state
) {
}