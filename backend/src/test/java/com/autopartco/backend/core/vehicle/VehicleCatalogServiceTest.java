package com.autopartco.backend.core.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.vehicle.internal.VehicleCatalogRepository;
import com.autopartco.backend.core.vehicle.internal.VehicleOptionView;
import com.autopartco.backend.shared.error.NotFoundException;

class VehicleCatalogServiceTest {
    private final VehicleCatalogRepository repository = mock(VehicleCatalogRepository.class);
    private final VehicleCatalogService service = new VehicleCatalogService(repository);
    private final UUID id = UUID.randomUUID();

    private static VehicleOptionView view(UUID id, String name) {
        VehicleOptionView view = mock(VehicleOptionView.class);
        when(view.getId()).thenReturn(id);
        when(view.getName()).thenReturn(name);
        return view;
    }

    @Test
    void makesMapIdAndName() {
        List<VehicleOptionView> rows = List.of(view(id, "BMW"));
        when(repository.findMakes()).thenReturn(rows);

        assertThat(service.makes()).containsExactly(new VehicleOption(id, "BMW"));
    }

    @Test
    void modelsOfExistingMake() {
        when(repository.makeExists(id)).thenReturn(true);
        List<VehicleOptionView> rows = List.of(view(id, "Serie 3"));
        when(repository.findModels(id)).thenReturn(rows);

        assertThat(service.models(id)).containsExactly(new VehicleOption(id, "Serie 3"));
    }

    @Test
    void modelsOfUnknownMakeIsNotFound() {
        when(repository.makeExists(id)).thenReturn(false);

        assertThatThrownBy(() -> service.models(id))
                .isInstanceOfSatisfying(NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("MAKE_NOT_FOUND"));
        verify(repository, never()).findModels(id);
    }

    @Test
    void versionsExposeLabel() {
        when(repository.modelExists(id)).thenReturn(true);
        List<VehicleOptionView> rows = List.of(view(id, "320i"));
        when(repository.findVersions(id)).thenReturn(rows);

        assertThat(service.versions(id)).containsExactly(new VersionOption(id, "320i"));
    }

    @Test
    void versionsOfUnknownModelIsNotFound() {
        when(repository.modelExists(id)).thenReturn(false);

        assertThatThrownBy(() -> service.versions(id))
                .isInstanceOfSatisfying(NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("MODEL_NOT_FOUND"));
        verify(repository, never()).findVersions(id);
    }
}
