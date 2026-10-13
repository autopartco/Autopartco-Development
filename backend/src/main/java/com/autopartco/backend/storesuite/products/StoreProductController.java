package com.autopartco.backend.storesuite.products;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.catalog.ProductCreateRequest;
import com.autopartco.backend.core.catalog.ProductPublicationRequest;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.catalog.ProductUpdateRequest;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** Productos del almacén para su equipo (permiso por código de almacén). */
@RestController
@RequestMapping(ApiPaths.STORES + "/{storeId}")
@AllArgsConstructor
public class StoreProductController {
    private final ListStoreProducts listStoreProducts;
    private final CreateProduct createProduct;
    private final UpdateProduct updateProduct;
    private final PublishProduct publishProduct;
    private final DeleteProduct deleteProduct;

    @GetMapping("/products")
    public List<ProductResponse> list(@PathVariable UUID storeId, Authentication authentication) {
        return listStoreProducts.execute(storeId, authentication.getName());
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> create(
            @PathVariable UUID storeId,
            @Valid @RequestBody ProductCreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(createProduct.execute(storeId, authentication.getName(), request));
    }

    @PatchMapping("/branches/{branchId}/products/{productId}")
    public ProductResponse update(
            @PathVariable UUID storeId,
            @PathVariable UUID branchId,
            @PathVariable UUID productId,
            @Valid @RequestBody ProductUpdateRequest request,
            Authentication authentication
    ) {
        return updateProduct.execute(storeId, branchId, productId, authentication.getName(), request);
    }

    @PatchMapping("/products/{productId}/publication")
    public ResponseEntity<Void> publish(
            @PathVariable UUID storeId,
            @PathVariable UUID productId,
            @Valid @RequestBody ProductPublicationRequest request,
            Authentication authentication
    ) {
        publishProduct.execute(storeId, productId, authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/products/{productId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID storeId,
            @PathVariable UUID productId,
            Authentication authentication
    ) {
        deleteProduct.execute(storeId, productId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
