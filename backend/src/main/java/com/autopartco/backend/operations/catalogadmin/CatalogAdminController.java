package com.autopartco.backend.operations.catalogadmin;

import com.autopartco.backend.operations.auditlog.AdminAudit;
import java.util.Map;

import org.springframework.security.core.Authentication;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.catalog.CategoryRequest;
import com.autopartco.backend.core.catalog.CategoryResponse;
import com.autopartco.backend.core.catalog.ManufacturerRequest;
import com.autopartco.backend.core.catalog.ManufacturerResponse;
import com.autopartco.backend.core.catalog.MasterDataService;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** Administración de los datos maestros del catálogo: categorías y fabricantes (solo root). */
@RestController
@RequestMapping(ApiPaths.ADMIN)
@AllArgsConstructor
public class CatalogAdminController {
    private final MasterDataService masterDataService;
    private final AdminAudit adminAudit;

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public List<CategoryResponse> categories() {
        return masterDataService.categories();
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request, Authentication authentication) {
        CategoryResponse category = masterDataService.createCategory(request);
        adminAudit.record(authentication, "CATEGORY_CREATED", "CATEGORY", category.id(), Map.of("code", category.code()));
        return ResponseEntity.status(HttpStatus.CREATED).body(category);
    }

    @PatchMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public CategoryResponse updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest request,
            Authentication authentication
    ) {
        CategoryResponse category = masterDataService.updateCategory(id, request);
        adminAudit.record(authentication, "CATEGORY_UPDATED", "CATEGORY", id, Map.of("code", category.code()));
        return category;
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public ResponseEntity<Void> deactivateCategory(@PathVariable UUID id, Authentication authentication) {
        masterDataService.deactivateCategory(id);
        adminAudit.record(authentication, "CATEGORY_DEACTIVATED", "CATEGORY", id, Map.of());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/manufacturers")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public List<ManufacturerResponse> manufacturers() {
        return masterDataService.manufacturers();
    }

    @PostMapping("/manufacturers")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public ResponseEntity<ManufacturerResponse> createManufacturer(@Valid @RequestBody ManufacturerRequest request, Authentication authentication) {
        ManufacturerResponse manufacturer = masterDataService.createManufacturer(request);
        adminAudit.record(authentication, "MANUFACTURER_CREATED", "MANUFACTURER", manufacturer.id(), Map.of("name", manufacturer.name()));
        return ResponseEntity.status(HttpStatus.CREATED).body(manufacturer);
    }

    @PatchMapping("/manufacturers/{id}")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public ManufacturerResponse updateManufacturer(
            @PathVariable UUID id,
            @Valid @RequestBody ManufacturerRequest request,
            Authentication authentication
    ) {
        ManufacturerResponse manufacturer = masterDataService.updateManufacturer(id, request);
        String action = Boolean.TRUE.equals(request.approved()) ? "MANUFACTURER_APPROVED" : "MANUFACTURER_UPDATED";
        adminAudit.record(authentication, action, "MANUFACTURER", id, Map.of("name", manufacturer.name()));
        return manufacturer;
    }

    @DeleteMapping("/manufacturers/{id}")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public ResponseEntity<Void> deactivateManufacturer(@PathVariable UUID id, Authentication authentication) {
        masterDataService.deactivateManufacturer(id);
        adminAudit.record(authentication, "MANUFACTURER_DEACTIVATED", "MANUFACTURER", id, Map.of());
        return ResponseEntity.noContent().build();
    }
}
