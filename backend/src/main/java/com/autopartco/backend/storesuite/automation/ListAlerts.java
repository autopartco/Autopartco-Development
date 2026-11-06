package com.autopartco.backend.storesuite.automation;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.shared.error.BadRequestException;

import lombok.AllArgsConstructor;

/**
 * Alertas del almacén (máximo 100, las más recientes primero) y su resumen. Requiere {@code STORE_READ}.
 * {@code status}: {@code open} (sin resolver, por defecto), {@code unread} (sin resolver ni leer) o {@code all};
 * otro valor: 400 {@code INVALID_ALERT_STATUS}.
 */
@Service
@AllArgsConstructor
public class ListAlerts {
    private final StoreAccess storeAccess;
    private final StoreAlertQueries queries;

    @Transactional(readOnly = true)
    public List<AlertResponse> list(UUID storeId, String email, String status) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_READ);
        String normalized = status == null || status.isBlank() ? "open" : status.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "open" -> queries.list(storeId, true, false);
            case "unread" -> queries.list(storeId, true, true);
            case "all" -> queries.list(storeId, false, false);
            default -> throw new BadRequestException("INVALID_ALERT_STATUS",
                    "El estado debe ser open, unread o all.");
        };
    }

    @Transactional(readOnly = true)
    public AlertSummaryResponse summary(UUID storeId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_READ);
        return queries.summary(storeId);
    }
}
