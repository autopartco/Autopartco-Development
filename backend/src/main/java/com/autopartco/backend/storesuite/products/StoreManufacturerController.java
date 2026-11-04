package com.autopartco.backend.storesuite.products;

import java.util.List;
import java.util.UUID;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.catalog.ManufacturerSearchResult;
import com.autopartco.backend.core.catalog.StoreManufacturerRequest;
import com.autopartco.backend.core.catalog.StoreManufacturerResponse;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** Fabricantes visibles al almacén y sugerencia de nuevos (pendientes de moderación). */
@RestController
@RequestMapping(ApiPaths.STORES + "/{storeId}/manufacturers")
@AllArgsConstructor
public class StoreManufacturerController {
    private final ManageStoreManufacturers manageStoreManufacturers;

    @GetMapping
    public ResponseEntity<List<StoreManufacturerResponse>> list(
            @PathVariable UUID storeId,
            Authentication authentication,
            WebRequest request
    ) {
        List<StoreManufacturerResponse> body = manageStoreManufacturers.list(storeId, authentication.getName());
        // ETag derivado del contenido: 304 si el cliente ya tiene la misma lista.
        String etag = "\"" + Integer.toHexString(body.hashCode()) + "-" + body.size() + "\"";
        if (request.checkNotModified(etag)) {
            return null;
        }
        return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache().cachePrivate()).body(body);
    }

    @GetMapping("/search")
    public ResponseEntity<List<ManufacturerSearchResult>> search(
            @PathVariable UUID storeId,
            @RequestParam(name = "q", required = false) String q,
            Authentication authentication
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePrivate())
                .body(manageStoreManufacturers.search(storeId, authentication.getName(), q));
    }

    @PostMapping
    public ResponseEntity<StoreManufacturerResponse> suggest(
            @PathVariable UUID storeId,
            @Valid @RequestBody StoreManufacturerRequest request,
            Authentication authentication
    ) {
        var result = manageStoreManufacturers.suggest(storeId, authentication.getName(), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.manufacturer());
    }
}
