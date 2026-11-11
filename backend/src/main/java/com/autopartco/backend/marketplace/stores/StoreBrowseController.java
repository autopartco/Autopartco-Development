package com.autopartco.backend.marketplace.stores;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.organization.PublicBranches;
import com.autopartco.backend.core.organization.PublicBranches.PublicBranch;
import com.autopartco.backend.core.organization.PublishedStoreProfile;
import com.autopartco.backend.core.organization.PublishedStores;
import com.autopartco.backend.core.organization.StoreFiles;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.core.organization.StoreServices;
import com.autopartco.backend.core.settings.SettingsQueries;
import com.autopartco.backend.core.settings.StoreSettings;
import com.autopartco.backend.marketplace.reviews.StoreReviewService;
import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

/** Almacenes publicados y sus recursos de marca, para compradores (público). */
@RestController
@RequestMapping(ApiPaths.BASE + "/marketplace/stores")
@AllArgsConstructor
public class StoreBrowseController {
    private final StoreService storeService;
    private final StoreFiles storeFiles;
    private final PublishedStores publishedStores;
    private final StoreReviewService storeReviewService;
    private final ListingQueries listingQueries;
    private final SettingsQueries settingsQueries;
    private final StoreServices storeServices;
    private final PublicBranches publicBranches;

    @GetMapping
    public List<PublicStoreSummary> published() {
        return storeService.published().stream().map(PublicStoreSummary::of).toList();
    }

    /** Perfil público: 404 {@code STORE_NOT_FOUND} si el almacén no está publicado. */
    @GetMapping("/{storeId}")
    public StorePublicProfile profile(@PathVariable UUID storeId) {
        PublishedStoreProfile store = publishedStores.profile(storeId);
        StoreSettings settings = settingsQueries.storeSettings(storeId);
        return StorePublicProfile.of(store, storeReviewService.rating(storeId),
                listingQueries.publishedProductCount(storeId), settings, storeServices.publicServices(storeId),
                stillPublished(storeId, settings.profile().featuredProductIds()));
    }

    /**
     * Sucursales activas del almacén publicado (404 {@code STORE_NOT_FOUND} si no). El WhatsApp de la sucursal cae al
     * del almacén; con {@code whatsappEnabled=false} ninguna sucursal expone WhatsApp.
     */
    @GetMapping("/{storeId}/branches")
    public List<PublicBranch> branches(@PathVariable UUID storeId) {
        List<PublicBranch> branches = publicBranches.list(storeId);
        if (!settingsQueries.storeSettings(storeId).contact().whatsappEnabled()) {
            return branches.stream().map(branch -> branch.withWhatsapp(null)).toList();
        }
        String storeWhatsapp = publishedStores.publicWhatsapp(storeId);
        return branches.stream()
                .map(branch -> branch.whatsapp() == null ? branch.withWhatsapp(storeWhatsapp) : branch)
                .toList();
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

    private List<UUID> stillPublished(UUID storeId, List<UUID> featured) {
        if (featured.isEmpty()) {
            return List.of();
        }
        Set<UUID> published = listingQueries.publishedProductIdsAmong(storeId, featured);
        return featured.stream().filter(published::contains).toList();
    }
}
