package com.autopartco.backend.marketplace.stores;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.autopartco.backend.core.organization.PublishedStoreProfile;
import com.autopartco.backend.marketplace.reviews.StoreRating;

/** Perfil público del almacén para compradores. {@code logoUrl}/{@code bannerUrl} son rutas de la API o nulos. */
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
        PublishedStoreProfile.PublicContact contact
) {
    static StorePublicProfile of(PublishedStoreProfile store, StoreRating rating, long publishedProducts) {
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
                store.contact()
        );
    }
}
