package com.autopartco.backend.operations.catalogadmin;

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

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public List<CategoryResponse> categories() {
        return masterDataService.categories();
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(masterDataService.createCategory(request));
    }

    @PatchMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public CategoryResponse updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest request
    ) {
        return masterDataService.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public ResponseEntity<Void> deactivateCategory(@PathVariable UUID id) {
        masterDataService.deactivateCategory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/manufacturers")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public List<ManufacturerResponse> manufacturers() {
        return masterDataService.manufacturers();
    }

    @PostMapping("/manufacturers")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public ResponseEntity<ManufacturerResponse> createManufacturer(@Valid @RequestBody ManufacturerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(masterDataService.createManufacturer(request));
    }

    @PatchMapping("/manufacturers/{id}")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public ManufacturerResponse updateManufacturer(
            @PathVariable UUID id,
            @Valid @RequestBody ManufacturerRequest request
    ) {
        return masterDataService.updateManufacturer(id, request);
    }

    @DeleteMapping("/manufacturers/{id}")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public ResponseEntity<Void> deactivateManufacturer(@PathVariable UUID id) {
        masterDataService.deactivateManufacturer(id);
        return ResponseEntity.noContent().build();
    }
}
