package com.autopartco.backend.product.mapper;

import org.springframework.stereotype.Component;

import com.autopartco.backend.product.dto.ProductImageResponse;
import com.autopartco.backend.product.entity.ProductImage;
import com.autopartco.backend.shared.storage.SupabaseStorageService;

import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class ProductImageMapper {
    private final SupabaseStorageService storageService;

    public ProductImageResponse toResponse(ProductImage image) {
        return new ProductImageResponse(
                image.getId(),
                storageService.publicUrl(image.getStoragePath()),
                image.isMain(),
                image.getDisplayOrder(),
                image.getCreatedAt()
        );
    }
}
