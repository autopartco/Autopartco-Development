package com.autopartco.backend.core.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.autopartco.backend.core.organization.internal.PublishedStoreAssets;
import com.autopartco.backend.core.organization.internal.Store;
import com.autopartco.backend.shared.error.NotFoundException;

class PublishedStoresTest {
    private final PublishedStoreAssets assets = mock(PublishedStoreAssets.class);
    private final JdbcClient jdbcClient = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
    private final PublishedStores publishedStores = new PublishedStores(assets, jdbcClient);
    private final UUID storeId = UUID.randomUUID();

    private Store store(String logoPath, Map<String, Object> configuration) {
        Store store = new Store();
        store.setId(storeId);
        store.setBusinessName("Repuestos Andes");
        store.setNit("900123456");
        store.setBusinessEmail("ventas@andes.co");
        store.setLogoPath(logoPath);
        store.setConfiguration(configuration);
        store.setCreatedAt(LocalDateTime.of(2026, 10, 1, 9, 30));
        return store;
    }

    @Test
    void buildsPublicProfileWithoutPrivateData() {
        when(assets.requirePublished(storeId)).thenReturn(store("stores/x/brand/logo/abc-123.png", Map.of(
                "description", "Frenos y suspensión",
                "publicProfile", Map.of("whatsapp", "+573001234567", "instagram", "andes", "website", " "))));
        when(jdbcClient.sql(anyString()).param(anyString(), any()).query(String.class).list())
                .thenReturn(List.of("Medellín", "bogotá", "Medellín", " "));

        PublishedStoreProfile profile = publishedStores.profile(storeId);

        assertThat(profile.name()).isEqualTo("Repuestos Andes");
        assertThat(profile.description()).isEqualTo("Frenos y suspensión");
        assertThat(profile.logoUrl()).isEqualTo("/api/v1/marketplace/stores/" + storeId + "/brand/logo?v=abc-123");
        assertThat(profile.bannerUrl()).isNull();
        assertThat(profile.cities()).containsExactly("bogotá", "Medellín");
        assertThat(profile.branchCount()).isEqualTo(4);
        assertThat(profile.memberSince()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(profile.contact()).isEqualTo(new PublishedStoreProfile.PublicContact(
                "+573001234567", null, null, "andes", null, null));
    }

    @Test
    void contactIsEmptyWhenConfigurationLacksIt() {
        when(assets.requirePublished(storeId)).thenReturn(store(null, null));
        when(jdbcClient.sql(anyString()).param(anyString(), any()).query(String.class).list()).thenReturn(List.of());

        PublishedStoreProfile profile = publishedStores.profile(storeId);

        assertThat(profile.contact()).isEqualTo(PublishedStoreProfile.PublicContact.EMPTY);
        assertThat(profile.description()).isNull();
        assertThat(profile.logoUrl()).isNull();
        assertThat(profile.cities()).isEmpty();
    }

    @Test
    void unpublishedStoreIsNotFound() {
        when(assets.requirePublished(storeId))
                .thenThrow(new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén publicado."));

        assertThatThrownBy(() -> publishedStores.profile(storeId))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("STORE_NOT_FOUND"));
    }
}
