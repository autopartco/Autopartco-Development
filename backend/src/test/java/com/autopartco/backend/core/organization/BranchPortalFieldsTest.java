package com.autopartco.backend.core.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.organization.internal.Branch;
import com.autopartco.backend.core.organization.internal.BranchRepository;
import com.autopartco.backend.core.organization.internal.Store;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.location.Location;
import com.autopartco.backend.shared.location.LocationRepository;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.persistence.StateRepository;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** WhatsApp, horario y punto en el mapa de una sucursal desde el portal. */
class BranchPortalFieldsTest {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private final BranchRepository branchRepository = mock(BranchRepository.class);
    private final LocationRepository locationRepository = mock(LocationRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final BranchService service =
            new BranchService(branchRepository, locationRepository, storeRepository, mock(StateRepository.class),
                    event -> { });
    private final UUID storeId = UUID.randomUUID();
    private final Branch branch = branch();

    private Branch branch() {
        Store store = new Store();
        store.setId(storeId);
        State active = new State();
        active.setCode("ACTIVE");
        Location location = Location.builder().addressLine("Cra 7").city("Bogotá").department("Bogotá D.C.")
                .country("Colombia").build();
        Branch branch = Branch.builder().store(store).name("Sede").numberPhone("6015551234").location(location)
                .state(active).whatsapp("+573001112233").openingHours(Map.of("mon", List.of())).build();
        branch.setId(UUID.randomUUID());
        return branch;
    }

    private BranchLocationRequest point(String latitude, String longitude) {
        return new BranchLocationRequest("Cra 7 # 10-20", null, "Bogotá", "Bogotá D.C.", null, null,
                latitude == null ? null : new BigDecimal(latitude), longitude == null ? null : new BigDecimal(longitude), null);
    }

    private BranchResponse update(BranchUpdateRequest request) {
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(new Store()));
        when(branchRepository.findInStore(branch.getId(), storeId)).thenReturn(Optional.of(branch));
        when(locationRepository.save(any(Location.class))).thenAnswer(call -> call.getArgument(0));
        return service.update(storeId, branch.getId(), request);
    }

    private static Set<String> invalidFields(BranchUpdateRequest request) {
        return VALIDATOR.validate(request).stream().map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void whatsappIsSetClearedAndKeptWhenNotSent() {
        BranchResponse set = update(new BranchUpdateRequest(null, null, null, "+573009998877", null));
        assertThat(set.whatsapp()).isEqualTo("+573009998877");

        assertThat(update(new BranchUpdateRequest("Nueva", null, null, null, null)).whatsapp())
                .isEqualTo("+573009998877");

        assertThat(update(new BranchUpdateRequest(null, null, null, "", null)).whatsapp()).isNull();
    }

    @Test
    void whatsappMustBeE164Colombia() {
        assertThat(invalidFields(new BranchUpdateRequest(null, null, null, "3001112233", null)))
                .containsExactly("whatsapp");
        assertThat(invalidFields(new BranchUpdateRequest(null, null, null, "+12025550123", null)))
                .containsExactly("whatsapp");
        assertThat(invalidFields(new BranchUpdateRequest(null, null, null, "+573001112233", null))).isEmpty();
        assertThat(invalidFields(new BranchUpdateRequest(null, null, null, "", null))).isEmpty();
    }

    @Test
    void openingHoursAreSavedNormalizedKeptWhenNullAndClearedWithAnEmptyObject() {
        BranchResponse saved = update(new BranchUpdateRequest(null, null, null, null,
                Map.of("tue", List.of(List.of("08:00", "17:00")), "notes", " Festivos 9-13 ")));
        assertThat(saved.openingHours()).containsEntry("notes", "Festivos 9-13").containsKey("tue");

        assertThat(update(new BranchUpdateRequest("Otra", null, null, null, null)).openingHours()).isNotNull();

        assertThat(update(new BranchUpdateRequest(null, null, null, null, Map.of())).openingHours()).isNull();
    }

    @Test
    void invalidOpeningHoursAreRejectedBeforeSaving() {
        assertThatThrownBy(() -> update(new BranchUpdateRequest(null, null, null, null,
                Map.of("mon", List.of(List.of("18:00", "08:00"))))))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("INVALID_OPENING_HOURS"));
    }

    @Test
    void anyOfTheNewFieldsCountsAsAChange() {
        assertThat(invalidFields(new BranchUpdateRequest(null, null, null, null, null))).containsExactly("changes");
        assertThat(invalidFields(new BranchUpdateRequest(null, null, null, null, Map.of()))).isEmpty();
        assertThat(invalidFields(new BranchUpdateRequest(null, null, null, "", null))).isEmpty();
    }

    @Test
    void coordinatesInsideColombiaAreSaved() {
        BranchResponse response = update(new BranchUpdateRequest(null, null, point("4.711", "-74.0721"), null, null));

        assertThat(response.location().latitude()).isEqualByComparingTo("4.711");
        assertThat(response.location().longitude()).isEqualByComparingTo("-74.0721");
    }

    @Test
    void coordinatesOutsideColombiaAreRejected() {
        for (String[] outside : new String[][] {{"40.4", "-3.7"}, {"-4.4", "-74"}, {"13.6", "-74"}, {"4.7", "-82.1"},
                {"4.7", "-66.7"}}) {
            assertThatThrownBy(() -> update(new BranchUpdateRequest(null, null, point(outside[0], outside[1]), null,
                    null)))
                    .as(outside[0] + "," + outside[1])
                    .isInstanceOfSatisfying(BadRequestException.class,
                            error -> assertThat(error.getCode()).isEqualTo("LOCATION_OUT_OF_RANGE"));
        }
        verify(locationRepository, never()).save(any());
    }

    @Test
    void theBordersOfColombiaAreInside() {
        for (String[] border : new String[][] {{"-4.3", "-82"}, {"13.5", "-66.8"}}) {
            assertThat(update(new BranchUpdateRequest(null, null, point(border[0], border[1]), null, null))
                    .location().latitude()).isEqualByComparingTo(border[0]);
        }
    }

    @Test
    void bothCoordinatesOrNone() {
        assertThatThrownBy(() -> update(new BranchUpdateRequest(null, null, point("4.7", null), null, null)))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("LOCATION_INCOMPLETE"));
    }

    @Test
    void withoutCoordinatesAChangedAddressIsRejected() {
        assertThatThrownBy(() -> update(new BranchUpdateRequest(null, null, point(null, null), null, null)))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("BRANCH_COORDINATES_REQUIRED"));
        verify(locationRepository, never()).save(any());
    }

    @Test
    void withoutCoordinatesTheSameAddressKeepsThePreviousPoint() {
        branch.setLocation(Location.builder().addressLine("Cra 7 # 10-20").city("Bogotá").department("Bogotá D.C.")
                .country("Colombia").latitude(new BigDecimal("4.6")).longitude(new BigDecimal("-74.08"))
                .metadata(Map.of(BranchService.APPROXIMATE, true)).build());

        BranchResponse response = update(new BranchUpdateRequest(null, null,
                new BranchLocationRequest(" cra 7 # 10-20 ", "Local 2", "Bogotá", "Bogotá D.C.", null, null, null, null,
                        null), null, null));

        assertThat(response.location().latitude()).isEqualByComparingTo("4.6");
        assertThat(response.location().longitude()).isEqualByComparingTo("-74.08");
        assertThat(response.location().approximate()).isTrue();
        assertThat(response.location().addressComplement()).isEqualTo("Local 2");
    }

    @Test
    void approximatePointIsSavedAndExactByDefault() {
        BranchResponse approximate = update(new BranchUpdateRequest(null, null,
                new BranchLocationRequest("Cra 7 # 10-20", null, "Bogotá", "Bogotá D.C.", null, null,
                        new BigDecimal("4.711"), new BigDecimal("-74.0721"), true), null, null));
        assertThat(approximate.location().approximate()).isTrue();

        assertThat(update(new BranchUpdateRequest(null, null, point("4.711", "-74.0721"), null, null))
                .location().approximate()).isFalse();
    }
}
