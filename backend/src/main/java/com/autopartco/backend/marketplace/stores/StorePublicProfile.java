package com.autopartco.backend.marketplace.stores;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.autopartco.backend.core.organization.PublicStoreService;
import com.autopartco.backend.core.organization.PublishedStoreProfile;
import com.autopartco.backend.core.settings.StoreSettings;
import com.autopartco.backend.marketplace.reviews.StoreRating;

/**
 * Perfil público del almacén para compradores. {@code logoUrl}/{@code bannerUrl} son rutas de la API o nulos.
 * {@code verified}: los almacenes publicados ya pasaron la verificación de la solicitud (o son anteriores al flujo).
 * {@code featuredProductIds} solo conserva los destacados aún publicados, en el orden configurado.
 */
public record StorePublicProfile(
        UUID id,
        String name,
        String description,
        String logoUrl,
        String bannerUrl,
        List<String> cities,
        int branchCount,
        LocalDate memberSince,
        StoreRating rating,
        long publishedProducts,
        PublishedStoreProfile.PublicContact contact,
        String tagline,
        boolean verified,
        boolean whatsappEnabled,
        String whatsappMessage,
        List<PublicStoreService> services,
        List<UUID> featuredProductIds
) {
    static StorePublicProfile of(
            PublishedStoreProfile store,
            StoreRating rating,
            long publishedProducts,
            StoreSettings settings,
            List<PublicStoreService> services,
            List<UUID> featuredProductIds
    ) {
        return new StorePublicProfile(
                store.id(),
                store.name(),
                store.description(),
                store.logoUrl(),
                store.bannerUrl(),
                store.cities(),
                store.branchCount(),
                store.memberSince(),
                rating,
                publishedProducts,
                store.contact(),
                settings.profile().tagline(),
                true,
                settings.contact().whatsappEnabled(),
                settings.contact().whatsappMessage(),
                services,
                featuredProductIds
        );
    }
}
