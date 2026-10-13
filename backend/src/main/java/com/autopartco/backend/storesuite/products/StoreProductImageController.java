package com.autopartco.backend.storesuite.products;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.core.catalog.ProductImageOrderRequest;
import com.autopartco.backend.core.catalog.ProductImageResponse;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** Imágenes de un producto del almacén (permiso {@code PRODUCT_IMAGE_MANAGE}). */
@RestController
@RequestMapping(ApiPaths.STORES + "/{storeId}/products/{productId}/images")
@AllArgsConstructor
public class StoreProductImageController {
    private final ManageProductImages manageProductImages;

    /** Acepta los campos multipart {@code file}, {@code files} y {@code files[]}, en ese orden. */
    @PostMapping
    public ResponseEntity<List<ProductImageResponse>> upload(
            @PathVariable UUID storeId,
            @PathVariable UUID productId,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "files[]", required = false) List<MultipartFile> filesArray,
            Authentication authentication
    ) {
        List<MultipartFile> uploads = new ArrayList<>();
        if (file != null) uploads.add(file);
        if (files != null) uploads.addAll(files);
        if (filesArray != null) uploads.addAll(filesArray);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(manageProductImages.upload(storeId, productId, authentication.getName(), uploads));
    }

    @PatchMapping("/{imageId}/main")
    public ProductImageResponse setMain(
            @PathVariable UUID storeId,
            @PathVariable UUID productId,
            @PathVariable UUID imageId,
            Authentication authentication
    ) {
        return manageProductImages.setMain(storeId, productId, imageId, authentication.getName());
    }

    @PatchMapping("/order")
    public List<ProductImageResponse> reorder(
            @PathVariable UUID storeId,
            @PathVariable UUID productId,
            @Valid @RequestBody ProductImageOrderRequest request,
            Authentication authentication
    ) {
        return manageProductImages.reorder(storeId, productId, authentication.getName(), request.imageIds());
    }

    @PutMapping("/{imageId}")
    public ProductImageResponse replace(
            @PathVariable UUID storeId,
            @PathVariable UUID productId,
            @PathVariable UUID imageId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return manageProductImages.replace(storeId, productId, imageId, authentication.getName(), file);
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID storeId,
            @PathVariable UUID productId,
            @PathVariable UUID imageId,
            Authentication authentication
    ) {
        manageProductImages.delete(storeId, productId, imageId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
