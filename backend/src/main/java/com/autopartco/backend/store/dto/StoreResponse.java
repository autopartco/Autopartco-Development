package com.autopartco.backend.store.dto;

import java.util.UUID;

public record StoreResponse(
        UUID id,
        String businessName,
        String nit,
        String businessEmail,
        String state
) {
}