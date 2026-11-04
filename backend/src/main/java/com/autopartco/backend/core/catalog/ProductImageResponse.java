package com.autopartco.backend.core.catalog;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code url} es el original; {@code thumbUrl} (400 px) y {@code cardUrl} (800 px) pasan por el proveedor de
 * transformación configurado ({@code image.provider}) o son iguales a {@code url} si no hay ninguno.
 */
public record ProductImageResponse(
        UUID id,
        String url,
        String thumbUrl,
        String cardUrl,
        boolean isMain,
        int displayOrder,
        LocalDateTime createdAt
) {
}
