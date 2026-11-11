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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.organization.internal.BranchRepository;
import com.autopartco.backend.core.organization.internal.OfferedService;
import com.autopartco.backend.core.organization.internal.OfferedServiceRepository;
import com.autopartco.backend.core.organization.internal.Store;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

class StoreServicesTest {
    private final OfferedServiceRepository serviceRepository = mock(OfferedServiceRepository.class);
    private final BranchRepository branchRepository = mock(BranchRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final StoreServices services = new StoreServices(serviceRepository, branchRepository, storeRepository);
    private final UUID storeId = UUID.randomUUID();
    private final UUID branchA = UUID.randomUUID();
    private final UUID branchB = UUID.randomUUID();

    private StoreServiceRequest request(List<UUID> branchIds) {
        return new StoreServiceRequest(ServiceCategory.ALIGNMENT_BALANCING, "  Alineación  ", "Con láser",
                new BigDecimal("60000"), 45, branchIds, 2, null, null);
    }

    private OfferedService existing(boolean active, UUID... branches) {
        OfferedService service = new OfferedService();
        service.setId(UUID.randomUUID());
        service.setStoreId(storeId);
        service.setCategory(ServiceCategory.TIRES);
        service.setName("Montaje");
        service.setActive(active);
        service.setVersion(3L);
        service.getBranchIds().addAll(Set.of(branches));
        return service;
    }

    private void storeAndBranchesExist() {
        when(storeRepository.lockById(storeId)).thenReturn(Optional.of(new Store()));
        when(branchRepository.findIdsInStore(storeId)).thenReturn(List.of(branchA, branchB));
        when(serviceRepository.saveAndFlush(any(OfferedService.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createsAnActiveServiceWithTrimmedNameAndItsBranches() {
        storeAndBranchesExist();

        StoreServiceView view = services.create(storeId, request(List.of(branchA, branchB)));

        assertThat(view.name()).isEqualTo("Alineación");
        assertThat(view.active()).isTrue();
        assertThat(view.priceFrom()).isEqualByComparingTo("60000");
        assertThat(view.sortOrder()).isEqualTo(2);
        assertThat(view.branchIds()).containsExactlyInAnyOrder(branchA, branchB);
    }

    @Test
    void branchOfAnotherStoreIsRejectedAndNothingIsSaved() {
        storeAndBranchesExist();

        assertThatThrownBy(() -> services.create(storeId, request(List.of(branchA, UUID.randomUUID()))))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("BRANCH_NOT_IN_STORE"));
        verify(serviceRepository, never()).saveAndFlush(any());
    }

    @Test
    void fiftyActiveServicesIsTheLimit() {
        storeAndBranchesExist();
        when(serviceRepository.countActiveInStore(storeId)).thenReturn(50L);

        assertThatThrownBy(() -> services.create(storeId, request(List.of(branchA))))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("SERVICE_LIMIT_REACHED"));
        verify(serviceRepository, never()).saveAndFlush(any());
    }

    @Test
    void theFiftiethServiceIsAllowed() {
        storeAndBranchesExist();
        when(serviceRepository.countActiveInStore(storeId)).thenReturn(49L);

        assertThat(services.create(storeId, request(List.of()))).isNotNull();
    }

    @Test
    void updateReplacesTheBranchesAndKeepsActiveWhenNotSent() {
        storeAndBranchesExist();
        OfferedService service = existing(true, branchA);
        when(serviceRepository.findInStore(service.getId(), storeId)).thenReturn(Optional.of(service));

        StoreServiceView view = services.update(storeId, service.getId(), request(List.of(branchB)));

        assertThat(view.branchIds()).containsExactly(branchB);
        assertThat(view.active()).isTrue();
        assertThat(view.category()).isEqualTo(ServiceCategory.ALIGNMENT_BALANCING);
    }

    @Test
    void serviceOfAnotherStoreIsNotFoundForReadUpdateAndDelete() {
        storeAndBranchesExist();
        UUID foreign = UUID.randomUUID();
        when(serviceRepository.findInStore(foreign, storeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> services.get(storeId, foreign)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> services.update(storeId, foreign, request(List.of())))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("SERVICE_NOT_FOUND"));
        assertThatThrownBy(() -> services.deactivate(storeId, foreign)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void staleVersionIsAConflict() {
        storeAndBranchesExist();
        OfferedService service = existing(true);
        when(serviceRepository.findInStore(service.getId(), storeId)).thenReturn(Optional.of(service));
        StoreServiceRequest stale = new StoreServiceRequest(ServiceCategory.TIRES, "Montaje", null, null, null,
                List.of(), null, null, 2L);

        assertThatThrownBy(() -> services.update(storeId, service.getId(), stale))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("SERVICE_VERSION_CONFLICT"));
    }

    @Test
    void reactivatingCountsAgainstTheLimit() {
        storeAndBranchesExist();
        OfferedService inactive = existing(false);
        when(serviceRepository.findInStore(inactive.getId(), storeId)).thenReturn(Optional.of(inactive));
        when(serviceRepository.countActiveInStore(storeId)).thenReturn(50L);
        StoreServiceRequest reactivate = new StoreServiceRequest(ServiceCategory.TIRES, "Montaje", null, null, null,
                List.of(), null, true, null);

        assertThatThrownBy(() -> services.update(storeId, inactive.getId(), reactivate))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("SERVICE_LIMIT_REACHED"));
        assertThat(inactive.isActive()).isFalse();
    }

    @Test
    void deleteDeactivatesWithoutRemovingTheRow() {
        OfferedService service = existing(true, branchA);
        when(serviceRepository.findInStore(service.getId(), storeId)).thenReturn(Optional.of(service));

        services.deactivate(storeId, service.getId());

        assertThat(service.isActive()).isFalse();
        verify(serviceRepository, never()).delete(any());
        verify(serviceRepository, never()).deleteById(any());
    }

    @Test
    void publicServicesOnlyListActiveBranches() {
        OfferedService service = existing(true, branchA, branchB);
        when(serviceRepository.findActiveInStore(storeId)).thenReturn(List.of(service));
        when(branchRepository.findActiveIdsInStore(storeId)).thenReturn(List.of(branchB));

        assertThat(services.publicServices(storeId)).singleElement()
                .satisfies(result -> assertThat(result.branchIds()).containsExactly(branchB));
    }

    @Test
    void noServicesMeansNoBranchQuery() {
        when(serviceRepository.findActiveInStore(storeId)).thenReturn(List.of());

        assertThat(services.publicServices(storeId)).isEmpty();
        verify(branchRepository, never()).findActiveIdsInStore(storeId);
    }

    @Test
    void requestValidationRejectsHtmlAndOutOfRangeValues() {
        var validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        StoreServiceRequest bad = new StoreServiceRequest(null, "<b>x</b>", "<script>", new BigDecimal("-1"), 4,
                List.of(), null, null, null);

        assertThat(validator.validate(bad)).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("category", "name", "description", "priceFrom", "durationMinutes");
        assertThat(validator.validate(new StoreServiceRequest(ServiceCategory.AC, "Aire", null, null, 10080,
                List.of(), null, null, null))).isEmpty();
    }
}
