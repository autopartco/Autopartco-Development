package com.autopartco.backend.storesuite.store;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.settings.StoreSettings;
import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

/** Configuración del almacén: objeto completo con valores por defecto; PATCH parcial. */
@RestController
@RequestMapping(ApiPaths.STORES + "/{storeId}/settings")
@AllArgsConstructor
public class StoreSettingsController {
    private final ManageStoreSettings manageStoreSettings;

    @GetMapping
    public StoreSettings get(@PathVariable UUID storeId, Authentication authentication) {
        return manageStoreSettings.get(storeId, authentication.getName());
    }

    @PatchMapping
    public StoreSettings patch(
            @PathVariable UUID storeId,
            @RequestBody Map<String, Object> patch,
            Authentication authentication
    ) {
        return manageStoreSettings.patch(storeId, authentication.getName(), patch);
    }
}
