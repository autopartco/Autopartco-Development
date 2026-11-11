package com.autopartco.backend.storesuite.services;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.organization.StoreServiceRequest;
import com.autopartco.backend.core.organization.StoreServiceView;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** Servicios que presta el almacén (portal). {@code DELETE} desactiva el servicio (204). */
@RestController
@RequestMapping(ApiPaths.STORES + "/{storeId}/services")
@AllArgsConstructor
public class StoreServiceController {
    private final ManageStoreServices manageStoreServices;

    @GetMapping
    public List<StoreServiceView> list(
            @PathVariable UUID storeId,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            Authentication authentication
    ) {
        return manageStoreServices.list(storeId, authentication.getName(), includeInactive);
    }

    @GetMapping("/{serviceId}")
    public StoreServiceView get(@PathVariable UUID storeId, @PathVariable UUID serviceId, Authentication authentication) {
        return manageStoreServices.get(storeId, serviceId, authentication.getName());
    }

    @PostMapping
    public ResponseEntity<StoreServiceView> create(
            @PathVariable UUID storeId,
            @Valid @RequestBody StoreServiceRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(manageStoreServices.create(storeId, authentication.getName(), request));
    }

    @PutMapping("/{serviceId}")
    public StoreServiceView update(
            @PathVariable UUID storeId,
            @PathVariable UUID serviceId,
            @Valid @RequestBody StoreServiceRequest request,
            Authentication authentication
    ) {
        return manageStoreServices.update(storeId, serviceId, authentication.getName(), request);
    }

    @DeleteMapping("/{serviceId}")
    public ResponseEntity<Void> deactivate(
            @PathVariable UUID storeId, @PathVariable UUID serviceId, Authentication authentication) {
        manageStoreServices.deactivate(storeId, serviceId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
