package com.autopartco.backend.storesuite.automation;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * Alertas automáticas del almacén. Lectura y cambio de estado con {@code STORE_READ}; preferencias con
 * {@code ALERT_MANAGE}.
 */
@RestController
@RequestMapping(ApiPaths.STORES + "/{storeId}")
@AllArgsConstructor
public class AlertsController {
    private final ListAlerts listAlerts;
    private final UpdateAlert updateAlert;
    private final AlertPreferences alertPreferences;

    @GetMapping("/alerts")
    public List<AlertResponse> alerts(
            @PathVariable UUID storeId,
            @RequestParam(required = false) String status,
            Authentication authentication
    ) {
        return listAlerts.list(storeId, authentication.getName(), status);
    }

    @GetMapping("/alerts/summary")
    public AlertSummaryResponse summary(@PathVariable UUID storeId, Authentication authentication) {
        return listAlerts.summary(storeId, authentication.getName());
    }

    @PatchMapping("/alerts/{alertId}")
    public AlertResponse update(
            @PathVariable UUID storeId,
            @PathVariable UUID alertId,
            @RequestBody AlertUpdateRequest request,
            Authentication authentication
    ) {
        return updateAlert.execute(storeId, alertId, authentication.getName(), request);
    }

    @GetMapping("/alert-preferences")
    public List<AlertPreferenceResponse> preferences(@PathVariable UUID storeId, Authentication authentication) {
        return alertPreferences.get(storeId, authentication.getName());
    }

    @PutMapping("/alert-preferences")
    public List<AlertPreferenceResponse> savePreferences(
            @PathVariable UUID storeId,
            @RequestBody List<@Valid AlertPreferenceRequest> request,
            Authentication authentication
    ) {
        return alertPreferences.save(storeId, authentication.getName(), request);
    }
}
