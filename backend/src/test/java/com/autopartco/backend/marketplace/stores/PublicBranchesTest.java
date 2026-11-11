package com.autopartco.backend.marketplace.stores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.organization.PublicBranches;
import com.autopartco.backend.core.organization.PublicBranches.PublicBranch;
import com.autopartco.backend.core.organization.PublishedStores;
import com.autopartco.backend.core.organization.StoreFiles;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.core.organization.StoreServices;
import com.autopartco.backend.core.organization.internal.Branch;
import com.autopartco.backend.core.organization.internal.BranchRepository;
import com.autopartco.backend.core.organization.internal.PublishedStoreAssets;
import com.autopartco.backend.core.settings.SettingsQueries;
import com.autopartco.backend.marketplace.reviews.StoreReviewService;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.location.Location;

/** {@code GET /marketplace/stores/{id}/branches}: sucursales activas, WhatsApp con respaldo y «abierto ahora». */
class PublicBranchesTest {
    /** Miércoles 2026-11-11 10:30 en Bogotá (UTC-5). */
    private static final Clock WEDNESDAY_1030 = Clock.fixed(Instant.parse("2026-11-11T15:30:00Z"), ZoneOffset.UTC);

    private final PublishedStoreAssets assets = mock(PublishedStoreAssets.class);
    private final BranchRepository branchRepository = mock(BranchRepository.class);
    private final StoreServices storeServices = mock(StoreServices.class);
    private final PublishedStores publishedStores = mock(PublishedStores.class);
    private final SettingsQueries settingsQueries = mock(SettingsQueries.class);
    private final UUID storeId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();

    private StoreBrowseController controller(Clock clock) {
        return new StoreBrowseController(mock(StoreService.class), mock(StoreFiles.class), publishedStores,
                mock(StoreReviewService.class), mock(ListingQueries.class), settingsQueries, storeServices,
                new PublicBranches(assets, branchRepository, storeServices, clock));
    }

    private Branch branch(String name, String phone, String whatsapp, Map<String, Object> hours, BigDecimal lat,
                          BigDecimal lng) {
        Location location = Location.builder()
                .addressLine("Cra 7 # 10-20").addressComplement("Local 3").city("Bogotá").department("Bogotá D.C.")
                .country("Colombia").latitude(lat).longitude(lng).build();
        Branch branch = Branch.builder().name(name).numberPhone(phone).location(location).whatsapp(whatsapp)
                .openingHours(hours).build();
        branch.setId(UUID.randomUUID());
        return branch;
    }

    private void settings(boolean whatsappEnabled) {
        when(settingsQueries.storeSettings(storeId)).thenReturn(
                StorePublicProfileTest.settings(whatsappEnabled, "Hola {enlace}", List.of(), null));
    }

    private static Map<String, Object> weekdays() {
        return Map.of("mon", List.of(List.of("08:00", "18:00")), "wed", List.of(List.of("08:00", "12:00"),
                List.of("14:00", "18:00")), "thu", List.of(), "notes", "Festivos 9-13");
    }

    @Test
    void mapsAddressPhoneCoordinatesAndServices() {
        Branch north = branch("Sede Norte", "6015551234", null, null, new BigDecimal("4.7110000"),
                new BigDecimal("-74.0721000"));
        when(branchRepository.findActiveInStore(storeId)).thenReturn(List.of(north));
        when(storeServices.serviceIdsByBranch(storeId)).thenReturn(Map.of(north.getId(), List.of(serviceId)));
        settings(true);
        when(publishedStores.publicWhatsapp(storeId)).thenReturn(null);

        List<PublicBranch> result = controller(WEDNESDAY_1030).branches(storeId);

        assertThat(result).singleElement().satisfies(branch -> {
            assertThat(branch.address()).isEqualTo("Cra 7 # 10-20 Local 3");
            assertThat(branch.phone()).isEqualTo("+576015551234");
            assertThat(branch.city()).isEqualTo("Bogotá");
            assertThat(branch.latitude()).isEqualByComparingTo("4.711");
            assertThat(branch.serviceIds()).containsExactly(serviceId);
            assertThat(branch.openingHours()).isNull();
            assertThat(branch.openNow()).isNull();
            assertThat(branch.whatsapp()).isNull();
        });
    }

    @Test
    void phoneThatIsNotTenDigitsIsReturnedAsIs() {
        Branch branch = branch("Sede", "6011234", null, null, null, null);
        when(branchRepository.findActiveInStore(storeId)).thenReturn(List.of(branch));
        when(storeServices.serviceIdsByBranch(storeId)).thenReturn(Map.of());
        settings(true);

        assertThat(controller(WEDNESDAY_1030).branches(storeId)).singleElement().satisfies(result -> {
            assertThat(result.phone()).isEqualTo("6011234");
            assertThat(result.latitude()).isNull();
            assertThat(result.serviceIds()).isEmpty();
        });
    }

    @Test
    void branchWhatsappFallsBackToTheStoreOneAndOwnOneWins() {
        Branch own = branch("Propia", "6015551234", "+573001112233", null, null, null);
        Branch inherited = branch("Heredada", "6015551234", null, null, null, null);
        when(branchRepository.findActiveInStore(storeId)).thenReturn(List.of(own, inherited));
        when(storeServices.serviceIdsByBranch(storeId)).thenReturn(Map.of());
        settings(true);
        when(publishedStores.publicWhatsapp(storeId)).thenReturn("+573009998877");

        assertThat(controller(WEDNESDAY_1030).branches(storeId)).extracting(PublicBranch::whatsapp)
                .containsExactly("+573001112233", "+573009998877");
    }

    @Test
    void whatsappIsNullForEveryBranchWhenTheStoreDisabledIt() {
        Branch own = branch("Propia", "6015551234", "+573001112233", null, null, null);
        when(branchRepository.findActiveInStore(storeId)).thenReturn(List.of(own));
        when(storeServices.serviceIdsByBranch(storeId)).thenReturn(Map.of());
        settings(false);

        assertThat(controller(WEDNESDAY_1030).branches(storeId)).extracting(PublicBranch::whatsapp)
                .containsOnlyNulls();
        verify(publishedStores, never()).publicWhatsapp(storeId);
    }

    @Test
    void openNowFollowsTheBogotaClock() {
        Branch branch = branch("Sede", "6015551234", null, weekdays(), null, null);
        when(branchRepository.findActiveInStore(storeId)).thenReturn(List.of(branch));
        when(storeServices.serviceIdsByBranch(storeId)).thenReturn(Map.of());
        settings(true);

        // Miércoles 10:30 Bogotá: dentro de 08:00-12:00.
        assertThat(controller(WEDNESDAY_1030).branches(storeId)).singleElement()
                .satisfies(result -> assertThat(result.openNow()).isTrue());
        // Miércoles 13:00 Bogotá: entre franjas.
        Clock lunch = Clock.fixed(Instant.parse("2026-11-11T18:00:00Z"), ZoneOffset.UTC);
        assertThat(controller(lunch).branches(storeId)).singleElement()
                .satisfies(result -> assertThat(result.openNow()).isFalse());
        // Jueves con franjas vacías: cerrado todo el día.
        Clock thursday = Clock.fixed(Instant.parse("2026-11-12T15:30:00Z"), ZoneOffset.UTC);
        assertThat(controller(thursday).branches(storeId)).singleElement()
                .satisfies(result -> assertThat(result.openNow()).isFalse());
        // 02:00 UTC del jueves ya es miércoles 21:00 en Bogotá: la zona no es UTC.
        Clock lateWednesday = Clock.fixed(Instant.parse("2026-11-12T02:00:00Z"), ZoneOffset.UTC);
        assertThat(controller(lateWednesday).branches(storeId)).singleElement()
                .satisfies(result -> assertThat(result.openNow()).isFalse());
    }

    @Test
    void unpublishedStoreIsNotFoundAndNothingElseIsQueried() {
        when(assets.requirePublished(storeId))
                .thenThrow(new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén publicado."));

        assertThatThrownBy(() -> controller(WEDNESDAY_1030).branches(storeId))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("STORE_NOT_FOUND"));
        verify(branchRepository, never()).findActiveInStore(storeId);
    }
}
