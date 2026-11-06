package com.autopartco.backend.storesuite.automation;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.analytics.AlertRules;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.shared.error.BadRequestException;

import lombok.AllArgsConstructor;

/**
 * Preferencias de alertas por tipo (canal en el portal, canal por correo y umbral). Requiere
 * {@code ALERT_MANAGE}. Sin fila guardada: {@code inApp} true, {@code email} false y sin umbral. Tipo desconocido
 * o repetido: 400 {@code INVALID_ALERT_TYPE}; umbral no permitido: 400 {@code INVALID_ALERT_THRESHOLD}.
 */
@Service
@AllArgsConstructor
public class AlertPreferences {
    private final StoreAccess storeAccess;
    private final StoreAlertQueries queries;

    @Transactional(readOnly = true)
    public List<AlertPreferenceResponse> get(UUID storeId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.ALERT_MANAGE);
        return all(queries.preferences(storeId));
    }

    /** Guarda (upsert) los tipos enviados y devuelve las cinco preferencias resultantes. */
    @Transactional
    public List<AlertPreferenceResponse> save(UUID storeId, String email, List<AlertPreferenceRequest> requested) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.ALERT_MANAGE);
        List<AlertPreferenceResponse> toSave = validate(requested);
        toSave.forEach(preference -> queries.savePreference(storeId, preference));
        return all(queries.preferences(storeId));
    }

    static List<AlertPreferenceResponse> validate(List<AlertPreferenceRequest> requested) {
        if (requested == null || requested.size() > AlertRules.Type.values().length) {
            throw new BadRequestException("INVALID_ALERT_TYPE", "Envía como máximo una preferencia por tipo.");
        }
        Set<AlertRules.Type> seen = new HashSet<>();
        return requested.stream().map(item -> {
            if (item == null) {
                throw new BadRequestException("INVALID_ALERT_TYPE", "Cada preferencia debe indicar su tipo.");
            }
            AlertRules.Type type = parseType(item.type());
            if (!seen.add(type)) {
                throw new BadRequestException("INVALID_ALERT_TYPE", "El tipo de alerta está repetido.");
            }
            Map<String, Object> threshold = AlertThresholds.validate(type, item.threshold());
            return new AlertPreferenceResponse(type.name(), item.inApp() == null || item.inApp(),
                    Boolean.TRUE.equals(item.email()), threshold);
        }).toList();
    }

    static List<AlertPreferenceResponse> all(Map<AlertRules.Type, AlertPreferenceResponse> saved) {
        return java.util.Arrays.stream(AlertRules.Type.values())
                .map(type -> saved.getOrDefault(type, AlertPreferenceResponse.defaults(type)))
                .toList();
    }

    private static AlertRules.Type parseType(String value) {
        try {
            return AlertRules.Type.valueOf((value == null ? "" : value).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new BadRequestException("INVALID_ALERT_TYPE",
                    "El tipo debe ser SALES_DROP, STOCKOUT_SOON, STOCKOUT, CUSTOMER_AT_RISK o DEAD_STOCK.");
        }
    }
}
