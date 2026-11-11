package com.autopartco.backend.core.settings;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

/**
 * Configuración de la cuenta en sesión. PATCH parcial: lo no enviado se conserva. La 2FA se cambia en
 * {@code /account/settings/two-factor/*} ({@code core.identity}).
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/account/settings")
@AllArgsConstructor
public class SettingsController {
    private final SettingsService settingsService;
    private final AccountLookup accountLookup;

    @GetMapping
    public AccountSettings get(Authentication authentication) {
        return settingsService.accountSettings(accountLookup.requireByEmail(authentication.getName()).accountId());
    }

    @PatchMapping
    public AccountSettings patch(@RequestBody Map<String, Object> patch, Authentication authentication) {
        return settingsService.patchAccount(accountLookup.requireByEmail(authentication.getName()).accountId(), patch);
    }
}
