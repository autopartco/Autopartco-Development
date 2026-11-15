package com.autopartco.backend.core.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.organization.internal.BranchRepository;
import com.autopartco.backend.core.organization.internal.CollaboratorRepository;
import com.autopartco.backend.core.organization.internal.OfferedService;
import com.autopartco.backend.core.organization.internal.OfferedServiceRepository;
import com.autopartco.backend.core.organization.internal.Store;
import com.autopartco.backend.core.organization.internal.StoreFileStorage;
import com.autopartco.backend.core.organization.internal.StorePermissionRepository;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.shared.events.ServicesChanged;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.persistence.StateRepository;

/**
 * Tipo de negocio del almacén (un taller {@code SERVICES} no necesita productos para tener su perfil completo: el
 * checklist del portal usa {@link BusinessType#requiresProducts}) y datos de agenda e imagen de sus servicios.
 */
class StoreProfileChecklistTest {
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final StoreService storeService = new StoreService(storeRepository, mock(CollaboratorRepository.class),
            mock(AccountLookup.class), mock(StateRepository.class), mock(StorePermissionRepository.class));
    private final UUID storeId = UUID.randomUUID();

    @Test
    void onlyWorkshopsCanOperateWithoutProducts() {
        assertThat(BusinessType.SERVICES.requiresProducts()).isFalse();
        assertThat(BusinessType.PARTS.requiresProducts()).isTrue();
        assertThat(BusinessType.BOTH.requiresProducts()).isTrue();
        assertThat(BusinessType.orDefault(null)).isEqualTo(BusinessType.PARTS);
    }

    @Test
    void profileUpdateChangesTheBusinessTypeAndReportsIt() {
        Store store = store();
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(store));

        StoreResponse response = storeService.update(storeId,
                new StoreUpdateRequest(null, null, null, null, null, BusinessType.SERVICES));

        assertThat(store.getBusinessType()).isEqualTo(BusinessType.SERVICES);
        assertThat(response.businessType()).isEqualTo(BusinessType.SERVICES);
        assertThat(storeService.profile(storeId).businessType()).isEqualTo(BusinessType.SERVICES);
    }

    @Test
    void businessTypeAloneIsAValidChangeAndOmittingItKeepsTheCurrentOne() {
        assertThat(new StoreUpdateRequest(null, null, null, null, null, BusinessType.BOTH).hasChanges()).isTrue();

        Store store = store();
        store.setBusinessType(BusinessType.BOTH);
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(store));
        storeService.update(storeId, new StoreUpdateRequest(null, null, null, "Taller", null));

        assertThat(store.getBusinessType()).isEqualTo(BusinessType.BOTH);
    }

    @Test
    void newStoresArePartsUnlessTheSignupSaysOtherwise() {
        assertThat(new StoreRegistrationRequest("Taller", "900", "t@x.co").businessType()).isNull();
        assertThat(Store.builder().businessName("x").build().getBusinessType()).isEqualTo(BusinessType.PARTS);
    }

    @Test
    void serviceBookingFieldsAreAppliedAndValidated() {
        OfferedServiceRepository repository = mock(OfferedServiceRepository.class);
        BranchRepository branches = mock(BranchRepository.class);
        when(storeRepository.lockById(storeId)).thenReturn(Optional.of(new Store()));
        when(repository.saveAndFlush(any(OfferedService.class))).thenAnswer(call -> call.getArgument(0));
        StoreServices services = new StoreServices(repository, branches, storeRepository,
                mock(ApplicationEventPublisher.class), mock(StoreFileStorage.class));

        StoreServiceView view = services.create(storeId, new StoreServiceRequest(ServiceCategory.AC, "Carga de gas",
                null, null, 60, List.of(), null, null, null, false, 90));

        assertThat(view.bookingEnabled()).isFalse();
        assertThat(view.slotMinutes()).isEqualTo(90);
        var validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validator.validate(new StoreServiceRequest(ServiceCategory.AC, "Aire", null, null, 60, List.of(),
                null, null, null, true, 45))).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("validSlotMinutes");
    }

    @Test
    void replacingTheImageStoresThePublicUrlAndDeletesThePreviousOne() {
        OfferedServiceRepository repository = mock(OfferedServiceRepository.class);
        StoreFileStorage files = mock(StoreFileStorage.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        OfferedService service = new OfferedService();
        UUID serviceId = UUID.randomUUID();
        service.setId(serviceId);
        service.setStoreId(storeId);
        service.setName("Alineación");
        service.setImageUrl("https://cdn/old.png");
        when(repository.findInStore(serviceId, storeId)).thenReturn(Optional.of(service));
        when(repository.saveAndFlush(any(OfferedService.class))).thenAnswer(call -> call.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1});
        when(files.uploadServiceImage(storeId, serviceId, file)).thenReturn("services/x/y/z.png");
        when(files.publicUrl("services/x/y/z.png")).thenReturn("https://cdn/services/x/y/z.png");
        StoreServices services = new StoreServices(repository, mock(BranchRepository.class), storeRepository, events,
                files);

        StoreServiceView view = services.replaceImage(storeId, serviceId, file);

        assertThat(view.imageUrl()).isEqualTo("https://cdn/services/x/y/z.png");
        verify(files).deletePublicUrlQuietly("https://cdn/old.png");
        verify(events).publishEvent(eq(ServicesChanged.of(serviceId)));

        assertThat(services.replaceImage(storeId, serviceId, null).imageUrl()).isNull();
    }

    private Store store() {
        State active = new State();
        active.setCode("ACTIVE");
        Store store = Store.builder().businessName("Taller").nit("900").businessEmail("t@x.co").state(active)
                .configuration(Map.of()).build();
        store.setId(storeId);
        return store;
    }

    @SuppressWarnings("unused")
    private static final BigDecimal UNUSED = BigDecimal.ZERO;
}
