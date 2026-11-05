package com.autopartco.backend.storesuite.automation;

import java.util.Map;

import com.autopartco.backend.core.analytics.AlertRules;

/** Preferencia de un tipo de alerta; sin fila guardada: {@code inApp} true, {@code email} false, sin umbral. */
public record AlertPreferenceResponse(String type, boolean inApp, boolean email, Map<String, Object> threshold) {
    static AlertPreferenceResponse defaults(AlertRules.Type type) {
        return new AlertPreferenceResponse(type.name(), true, false, null);
    }
}
