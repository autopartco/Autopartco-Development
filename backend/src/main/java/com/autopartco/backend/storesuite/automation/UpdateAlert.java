package com.autopartco.backend.storesuite.automation;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/**
 * Marca una alerta como leída o resuelta (resolver implica leída). Requiere {@code STORE_READ}. Alerta inexistente
 * o de otro almacén: 404 {@code ALERT_NOT_FOUND}. Cuerpo sin {@code read} ni {@code resolved} en true: 400
 * {@code INVALID_ALERT_UPDATE}.
 */
@Service
@AllArgsConstructor
public class UpdateAlert {
    private final StoreAccess storeAccess;
    private final StoreAlertQueries queries;

    @Transactional
    public AlertResponse execute(UUID storeId, UUID alertId, String email, AlertUpdateRequest request) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_READ);
        if (request == null || (!request.markRead() && !request.markResolved())) {
            throw new BadRequestException("INVALID_ALERT_UPDATE", "Indica read o resolved en true.");
        }
        boolean resolved = request.markResolved();
        return queries.update(storeId, alertId, request.markRead() || resolved, resolved)
                .orElseThrow(() -> new NotFoundException("ALERT_NOT_FOUND", "No se encontró la alerta."));
    }
}
