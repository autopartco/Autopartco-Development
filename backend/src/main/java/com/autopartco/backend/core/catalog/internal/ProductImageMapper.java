package com.autopartco.backend.core.catalog.internal;

import org.springframework.stereotype.Component;

import com.autopartco.backend.core.catalog.ProductImageResponse;
import com.autopartco.backend.shared.storage.ImageUrls;
import com.autopartco.backend.shared.storage.ImageVariant;

import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class ProductImageMapper {
    private final ImageUrls imageUrls;

    public ProductImageResponse toResponse(ProductImage image) {
        String path = image.getStoragePath();
        return new ProductImageResponse(
                image.getId(),
                imageUrls.original(path),
                imageUrls.variant(path, ImageVariant.THUMB),
                imageUrls.variant(path, ImageVariant.CARD),
                image.isMain(),
                image.getDisplayOrder(),
                image.getCreatedAt()
        );
    }
}
