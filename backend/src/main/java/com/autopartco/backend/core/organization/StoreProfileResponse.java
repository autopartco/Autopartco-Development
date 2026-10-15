package com.autopartco.backend.core.organization;

import java.util.UUID;

/** Perfil público-interno del almacén. {@code logoUrl} y {@code bannerUrl} quedan nulos hasta que exista soporte de imágenes de tienda. */
public record StoreProfileResponse(
        UUID id,
        String businessName,
        String nit,
        String businessEmail,
        String state,
        String description,
        String logoUrl,
        String bannerUrl
) {
}
