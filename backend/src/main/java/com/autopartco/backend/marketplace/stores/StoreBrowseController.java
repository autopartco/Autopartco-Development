package com.autopartco.backend.marketplace.stores;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.organization.StoreFiles;
import com.autopartco.backend.core.organization.StoreResponse;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

/** Almacenes publicados y sus recursos de marca, para compradores (público). */
@RestController
@RequestMapping(ApiPaths.BASE + "/marketplace/stores")
@AllArgsConstructor
public class StoreBrowseController {
    private final StoreService storeService;
    private final StoreFiles storeFiles;

    @GetMapping
    public List<StoreResponse> published() {
        return storeService.published();
    }

    @GetMapping("/{storeId}/brand/{assetType}")
    public ResponseEntity<byte[]> brandAsset(@PathVariable UUID storeId, @PathVariable String assetType) {
        return storeFiles.publicBrandAsset(storeId, assetType)
                .map(stored -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(stored.contentType()))
                        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                        .body(stored.content()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
