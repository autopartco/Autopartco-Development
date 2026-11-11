package com.autopartco.backend.core.organization;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Datos públicos de un almacén publicado. Nunca incluye NIT ni correo del almacén.
 *
 * @param logoUrl URL pública versionada ({@link StoreBrand#url}) o {@code null}
 * @param cities ciudades de las sucursales activas, sin repetir y en orden alfabético
 * @param branchCount sucursales activas
 */
public record PublishedStoreProfile(
        UUID id,
        String name,
        String description,
        String logoUrl,
        String bannerUrl,
        List<String> cities,
        int branchCount,
        LocalDate memberSince,
        PublicContact contact
) {
    /** Contacto público que el almacén decide mostrar (todos opcionales). */
    public record PublicContact(
            String whatsapp,
            String phone,
            String website,
            String instagram,
            String facebook,
            String tiktok,
            String youtube,
            String x,
            String email
    ) {
        public static final PublicContact EMPTY =
                new PublicContact(null, null, null, null, null, null, null, null, null);
    }
}
