package com.autopartco.backend.core.organization;

import java.util.UUID;

/**
 * Perfil del almacén para su portal. {@code logoUrl}/{@code bannerUrl}: URL pública versionada ({@link StoreBrand#url}),
 * que solo responde si el almacén está publicado; {@code null} si no hay archivo.
 */
public record StoreProfileResponse(
        UUID id,
        String businessName,
        String nit,
        String businessEmail,
        String state,
        String description,
        String logoUrl,
        String bannerUrl,
        PublishedStoreProfile.PublicContact publicProfile
) {
}
