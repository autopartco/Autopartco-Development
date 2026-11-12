package com.autopartco.backend.core.notification;

import java.time.LocalDateTime;
import java.util.UUID;

/** Notificación in-app. {@code link}: ruta interna del frontend ({@code /…}) a la que lleva, o {@code null}. */
public record NotificationResponse(
        UUID id,
        String title,
        String message,
        String link,
        boolean read,
        LocalDateTime createdAt
) {
}
