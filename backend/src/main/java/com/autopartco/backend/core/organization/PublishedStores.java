package com.autopartco.backend.core.organization;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.internal.PublishedStoreAssets;
import com.autopartco.backend.core.organization.internal.Store;

import lombok.AllArgsConstructor;

/** Almacenes publicados vistos por compradores: perfil público y verificación de publicación. */
@Service
@AllArgsConstructor
public class PublishedStores {
    static final String DESCRIPTION_KEY = "description";
    static final String PUBLIC_PROFILE_KEY = "publicProfile";

    static final String ACTIVE_BRANCH_CITIES_SQL = """
            SELECT l.city
            FROM branches b
            JOIN states bs ON bs.id = b.state AND bs.deleted_at IS NULL
            JOIN locations l ON l.id = b.location_id
            WHERE b.store_id = :storeId AND b.deleted_at IS NULL AND bs.code = 'ACTIVE'
            """;

    private final PublishedStoreAssets publishedStoreAssets;
    private final JdbcClient jdbcClient;

    /** Exige que el almacén esté publicado; si no, {@code STORE_NOT_FOUND} (404). */
    @Transactional(readOnly = true)
    public void requirePublished(UUID storeId) {
        publishedStoreAssets.requirePublished(storeId);
    }

    @Transactional(readOnly = true)
    public PublishedStoreProfile profile(UUID storeId) {
        Store store = publishedStoreAssets.requirePublished(storeId);
        Map<String, Object> configuration = store.getConfiguration() == null ? Map.of() : store.getConfiguration();
        List<String> branchCities = jdbcClient.sql(ACTIVE_BRANCH_CITIES_SQL)
                .param("storeId", storeId)
                .query(String.class)
                .list();
        return new PublishedStoreProfile(
                store.getId(),
                store.getBusinessName(),
                text(configuration.get(DESCRIPTION_KEY)),
                StoreBrand.url(storeId, StoreBrand.LOGO, store.getLogoPath()),
                StoreBrand.url(storeId, StoreBrand.BANNER, store.getBannerPath()),
                cities(branchCities),
                branchCities.size(),
                store.getCreatedAt() == null ? null : store.getCreatedAt().toLocalDate(),
                contact(configuration.get(PUBLIC_PROFILE_KEY))
        );
    }

    static List<String> cities(List<String> branchCities) {
        return branchCities.stream()
                .filter(city -> city != null && !city.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public static PublishedStoreProfile.PublicContact contact(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return PublishedStoreProfile.PublicContact.EMPTY;
        }
        return new PublishedStoreProfile.PublicContact(
                text(map.get("whatsapp")),
                text(map.get("phone")),
                text(map.get("website")),
                text(map.get("instagram")),
                text(map.get("facebook")),
                text(map.get("tiktok")),
                text(map.get("youtube")),
                text(map.get("x")),
                text(map.get("email"))
        );
    }

    /** WhatsApp público del almacén ({@code publicProfile.whatsapp}) o {@code null}; exige almacén publicado. */
    @Transactional(readOnly = true)
    public String publicWhatsapp(UUID storeId) {
        Store store = publishedStoreAssets.requirePublished(storeId);
        Map<String, Object> configuration = store.getConfiguration() == null ? Map.of() : store.getConfiguration();
        return contact(configuration.get(PUBLIC_PROFILE_KEY)).whatsapp();
    }

    private static String text(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }
}
