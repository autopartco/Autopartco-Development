package com.autopartco.backend.seller.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record SellerNotificationResponse(
        UUID id,
        String title,
        String message,
        boolean read,
        LocalDateTime createdAt
) {
}
