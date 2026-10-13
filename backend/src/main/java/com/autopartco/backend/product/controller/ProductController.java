package com.autopartco.backend.product.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.core.catalog.ProductCreateRequest;
import com.autopartco.backend.core.catalog.ProductPublicationRequest;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.catalog.ProductOptionsResponse;
import com.autopartco.backend.core.catalog.ProductImageOrderRequest;
import com.autopartco.backend.core.catalog.ProductImageResponse;
import com.autopartco.backend.core.catalog.ProductUpdateRequest;
import com.autopartco.backend.product.service.ProductImageService;
import com.autopartco.backend.product.service.ProductService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(ApiPaths.BASE)
@AllArgsConstructor
public class ProductController {
    private final ProductService productService;
    private final ProductImageService productImageService;

    @GetMapping("/stores/{storeId}/products")
    public List<ProductResponse> list(@PathVariable UUID storeId, Authentication authentication) {
        return productService.list(storeId, authentication.getName());
    }

    @PostMapping("/stores/{storeId}/products")
    public ResponseEntity<ProductResponse> create(
            @PathVariable UUID storeId,
            @Valid @RequestBody ProductCreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.create(storeId, authentication.getName(), request));
    }

    @PatchMapping("/stores/{storeId}/branches/{branchId}/products/{productId}")
    public ProductResponse update(
            @PathVariable UUID storeId,
            @PathVariable UUID branchId,
            @PathVariable UUID productId,
            @Valid @RequestBody ProductUpdateRequest request,
            Authentication authentication
    ) {
        return productService.update(storeId, branchId, productId, authentication.getName(), request);
    }

    @PatchMapping("/stores/{storeId}/products/{productId}/publication")
    public ResponseEntity<Void> publish(
            @PathVariable UUID storeId,
            @PathVariable UUID productId,
            @Valid @RequestBody ProductPublicationRequest request,
            Authentication authentication
    ) {
        productService.publish(storeId, productId, authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/products/{productId}")
    public ProductResponse getPublished(@PathVariable UUID productId) {
        return productService.published(productId);
    }

    @PostMapping("/stores/{storeId}/products/{productId}/images")
    public ResponseEntity<List<ProductImageResponse>> uploadImages(
            @PathVariable UUID storeId,
            @PathVariable UUID productId,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "files[]", required = false) List<MultipartFile> filesArray,
            Authentication authentication
    ) {
        List<MultipartFile> uploads = new java.util.ArrayList<>();
        if (file != null) uploads.add(file);
        if (files != null) uploads.addAll(files);
        if (filesArray != null) uploads.addAll(filesArray);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productImageService.upload(storeId, productId, authentication.getName(), uploads));
    }

    @PatchMapping("/stores/{storeId}/products/{productId}/images/{imageId}/main")
    public ProductImageResponse setMainImage(
            @PathVariable UUID storeId,
            @PathVariable UUID productId,
            @PathVariable UUID imageId,
            Authentication authentication
    ) {
        return productImageService.setMain(storeId, productId, imageId, authentication.getName());
    }

    @PatchMapping("/stores/{storeId}/products/{productId}/images/order")
    public List<ProductImageResponse> reorderImages(
            @PathVariable UUID storeId,
            @PathVariable UUID productId,
            @Valid @RequestBody ProductImageOrderRequest request,
            Authentication authentication
    ) {
        return productImageService.reorder(
                storeId, productId, authentication.getName(), request.imageIds());
    }

    @PutMapping("/stores/{storeId}/products/{productId}/images/{imageId}")
    public ProductImageResponse replaceImage(
            @PathVariable UUID storeId,
            @PathVariable UUID productId,
            @PathVariable UUID imageId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return productImageService.replace(
                storeId, productId, imageId, authentication.getName(), file);
    }

    @DeleteMapping("/stores/{storeId}/products/{productId}/images/{imageId}")
    public ResponseEntity<Void> deleteImage(
            @PathVariable UUID storeId,
            @PathVariable UUID productId,
            @PathVariable UUID imageId,
            Authentication authentication
    ) {
        productImageService.delete(storeId, productId, imageId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/stores/{storeId}/products/{productId}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable UUID storeId,
            @PathVariable UUID productId,
            Authentication authentication
    ) {
        productImageService.deleteProduct(storeId, productId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/products/published")
    public List<ProductResponse> published() {
        return productService.published();
    }

    @GetMapping("/products/options")
    public ProductOptionsResponse options() {
        return productService.options();
    }

    @GetMapping("/products/options/manufacturers")
    public List<ProductOptionsResponse.ManufacturerOption> manufacturers() {
        return productService.manufacturers();
    }

    @GetMapping("/products/options/categories")
    public List<ProductOptionsResponse.CategoryOption> categories() {
        return productService.categories();
    }

    @GetMapping("/products/options/compatibilities")
    public List<ProductOptionsResponse.CompatibilityOption> compatibilities() {
        return productService.compatibilities();
    }
}