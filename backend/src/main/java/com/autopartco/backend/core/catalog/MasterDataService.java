package com.autopartco.backend.core.catalog;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.core.catalog.internal.Category;
import com.autopartco.backend.core.catalog.internal.Manufacturer;
import com.autopartco.backend.core.catalog.internal.CategoryRepository;
import com.autopartco.backend.core.catalog.internal.ManufacturerRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class MasterDataService {
    private final CategoryRepository categoryRepository;
    private final ManufacturerRepository manufacturerRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return categoryRepository.findAllByOrderByCodeAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (categoryRepository.codeExists(code)) {
            throw new ConflictException("CATEGORY_ALREADY_EXISTS", "Ya existe una categoría con ese código.");
        }
        return toResponse(categoryRepository.save(Category.builder().code(code).build()));
    }

    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        Category category = findCategory(id);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (categoryRepository.codeUsedByOther(code, id)) {
            throw new ConflictException("CATEGORY_ALREADY_EXISTS", "Ya existe una categoría con ese código.");
        }
        category.setCode(code);
        return toResponse(category);
    }

    @Transactional
    public void deactivateCategory(UUID id) {
        categoryRepository.delete(findCategory(id));
    }

    @Transactional(readOnly = true)
    public List<ManufacturerResponse> manufacturers() {
        return manufacturerRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public ManufacturerResponse createManufacturer(ManufacturerRequest request) {
        String name = request.name().trim();
        if (manufacturerRepository.nameExists(name)) {
            throw new ConflictException("MANUFACTURER_ALREADY_EXISTS", "Ya existe un fabricante con ese nombre.");
        }
        return toResponse(manufacturerRepository.save(Manufacturer.builder().name(name).build()));
    }

    @Transactional
    public ManufacturerResponse updateManufacturer(UUID id, ManufacturerRequest request) {
        Manufacturer manufacturer = findManufacturer(id);
        String name = request.name().trim();
        if (manufacturerRepository.nameUsedByOther(name, id)) {
            throw new ConflictException("MANUFACTURER_ALREADY_EXISTS", "Ya existe un fabricante con ese nombre.");
        }
        manufacturer.setName(name);
        return toResponse(manufacturer);
    }

    @Transactional
    public void deactivateManufacturer(UUID id) {
        manufacturerRepository.delete(findManufacturer(id));
    }

    private Category findCategory(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "No se encontró la categoría activa."));
    }

    private Manufacturer findManufacturer(UUID id) {
        return manufacturerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "MANUFACTURER_NOT_FOUND",
                        "No se encontró el fabricante activo."
                ));
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getCode());
    }

    private ManufacturerResponse toResponse(Manufacturer manufacturer) {
        return new ManufacturerResponse(manufacturer.getId(), manufacturer.getName());
    }
}