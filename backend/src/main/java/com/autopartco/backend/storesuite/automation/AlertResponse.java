package com.autopartco.backend.storesuite.automation;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** Alerta del almacén. {@code readAt} y {@code resolvedAt} son {@code null} mientras no se lea o resuelva. */
public record AlertResponse(
        UUID id,
        String type,
        String severity,
        String title,
        String message,
        Map<String, Object> payload,
        OffsetDateTime createdAt,
        OffsetDateTime readAt,
        OffsetDateTime resolvedAt
) {
}
