package com.autopartco.backend.core.notification;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Notificación in-app. {@code link}: ruta interna del frontend ({@code /…}) a la que lleva, o {@code null}.
 * {@code archived}: está en el histórico (se «limpió» de la bandeja).
 */
public record NotificationResponse(
        UUID id,
        NotificationKind kind,
        String title,
        String message,
        String link,
        boolean read,
        boolean archived,
        LocalDateTime createdAt
) {
}
