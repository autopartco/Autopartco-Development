package com.autopartco.backend.core.identity.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.identity.internal.TwoFactorSettingsService;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * Activar/desactivar la verificación en dos pasos por correo de la cuenta en sesión. El resto de la configuración
 * de la cuenta está en {@code GET/PATCH /account/settings} ({@code core.settings}).
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/account/settings/two-factor")
@AllArgsConstructor
public class TwoFactorSettingsController {
    private final TwoFactorSettingsService twoFactorSettingsService;

    /** Envía un código al correo para {@code action} = {@code ENABLE} (por defecto) o {@code DISABLE}. */
    @PostMapping("/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TwoFactorSetupResponse request(
            @Valid @RequestBody(required = false) TwoFactorSetupRequest request,
            Authentication authentication
    ) {
        String action = request == null ? TwoFactorSettingsService.ENABLE : request.actionOrDefault();
        return twoFactorSettingsService.request(authentication.getName(), action);
    }

    @PostMapping("/confirm")
    public TwoFactorStatusResponse confirm(@Valid @RequestBody TwoFactorConfirmRequest request, Authentication authentication) {
        return twoFactorSettingsService.confirm(authentication.getName(), request.code(), request.actionOrDefault());
    }

    @PostMapping("/disable")
    public TwoFactorStatusResponse disable(@Valid @RequestBody TwoFactorDisableRequest request, Authentication authentication) {
        return twoFactorSettingsService.disableWithPassword(authentication.getName(), request.password());
    }
}
