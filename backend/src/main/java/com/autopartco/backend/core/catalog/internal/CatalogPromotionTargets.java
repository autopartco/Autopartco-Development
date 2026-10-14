package com.autopartco.backend.core.catalog.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.core.pricing.PromotionTargets;

import lombok.AllArgsConstructor;

/** Implementación de la SPI {@link PromotionTargets} de {@code pricing} con los datos de catálogo y almacenes. */
@Component
@AllArgsConstructor
public class CatalogPromotionTargets implements PromotionTargets {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final StoreService storeService;

    @Override
    @Transactional(readOnly = true)
    public Optional<TargetProduct> findProduct(UUID productId) {
        return productRepository.findById(productId)
                .map(product -> new TargetProduct(product.getId(), product.getStoreId()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean categoryExists(UUID categoryId) {
        return categoryRepository.existsById(categoryId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean manufacturerExists(UUID manufacturerId) {
        return manufacturerRepository.existsById(manufacturerId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean storeExists(UUID storeId) {
        return storeService.exists(storeId);
    }
}
