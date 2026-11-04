package com.autopartco.backend.marketplace.catalog;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import com.autopartco.backend.core.vehicle.VehicleCatalogService;
import com.autopartco.backend.core.vehicle.VehicleOption;
import com.autopartco.backend.core.vehicle.VersionOption;
import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

/** Catálogo de vehículos en cascada (público). Cacheable 1 h con ETag; 304 si {@code If-None-Match} coincide. */
@RestController
@AllArgsConstructor
public class VehicleCatalogController {
    private static final String VEHICLES = ApiPaths.MARKETPLACE + "/vehicles";
    private static final CacheControl CACHE = CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic();

    private final VehicleCatalogService vehicleCatalog;

    @GetMapping(VEHICLES + "/makes")
    public ResponseEntity<List<VehicleOption>> makes(WebRequest request) {
        return cached(request, vehicleCatalog.makes());
    }

    @GetMapping(VEHICLES + "/makes/{makeId}/models")
    public ResponseEntity<List<VehicleOption>> models(@PathVariable UUID makeId, WebRequest request) {
        return cached(request, vehicleCatalog.models(makeId));
    }

    @GetMapping(VEHICLES + "/models/{modelId}/versions")
    public ResponseEntity<List<VersionOption>> versions(@PathVariable UUID modelId, WebRequest request) {
        return cached(request, vehicleCatalog.versions(modelId));
    }

    private static <T> ResponseEntity<List<T>> cached(WebRequest request, List<T> body) {
        // Records con UUID/String: hashCode determinista entre arranques.
        String etag = "\"" + Integer.toHexString(body.hashCode()) + "-" + body.size() + "\"";
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(304).eTag(etag).cacheControl(CACHE).build();
        }
        return ResponseEntity.ok().eTag(etag).cacheControl(CACHE).body(body);
    }
}
