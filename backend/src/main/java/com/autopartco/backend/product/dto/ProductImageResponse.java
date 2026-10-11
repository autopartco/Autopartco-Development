package com.autopartco.backend.product.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProductImageResponse(
        UUID id,
        String url,
        boolean isMain,
        int displayOrder,
        LocalDateTime createdAt
) {
}
