package com.autopartco.backend.product.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.product.dto.CategoryRequest;
import com.autopartco.backend.product.dto.CategoryResponse;
import com.autopartco.backend.product.dto.ManufacturerRequest;
import com.autopartco.backend.product.dto.ManufacturerResponse;
import com.autopartco.backend.product.service.MasterDataService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(ApiPaths.BASE)
@AllArgsConstructor
public class MasterDataController {
    private final MasterDataService masterDataService;

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return masterDataService.categories();
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(masterDataService.createCategory(request));
    }

    @PatchMapping("/categories/{id}")
    public CategoryResponse updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest request
    ) {
        return masterDataService.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deactivateCategory(@PathVariable UUID id) {
        masterDataService.deactivateCategory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/manufacturers")
    public List<ManufacturerResponse> manufacturers() {
        return masterDataService.manufacturers();
    }

    @PostMapping("/manufacturers")
    public ResponseEntity<ManufacturerResponse> createManufacturer(@Valid @RequestBody ManufacturerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(masterDataService.createManufacturer(request));
    }

    @PatchMapping("/manufacturers/{id}")
    public ManufacturerResponse updateManufacturer(
            @PathVariable UUID id,
            @Valid @RequestBody ManufacturerRequest request
    ) {
        return masterDataService.updateManufacturer(id, request);
    }

    @DeleteMapping("/manufacturers/{id}")
    public ResponseEntity<Void> deactivateManufacturer(@PathVariable UUID id) {
        masterDataService.deactivateManufacturer(id);
        return ResponseEntity.noContent().build();
    }
}