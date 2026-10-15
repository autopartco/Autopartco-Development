package com.autopartco.backend.core.organization;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.internal.Branch;
import com.autopartco.backend.core.organization.internal.BranchRepository;
import com.autopartco.backend.core.organization.internal.Store;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.location.Location;
import com.autopartco.backend.shared.location.LocationRepository;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.persistence.StateCodes;
import com.autopartco.backend.shared.persistence.StateRepository;

import lombok.AllArgsConstructor;

/**
 * Sucursales de un almacén, siempre acotadas por {@code storeId}. No resuelve permisos ni límites de plan:
 * lo hacen los casos de uso de {@code storesuite.branches}.
 */
@Service
@AllArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final LocationRepository locationRepository;
    private final StoreRepository storeRepository;
    private final StateRepository stateRepository;

    @Transactional(readOnly = true)
    public List<BranchResponse> list(UUID storeId) {
        requireStore(storeId);
        return branchRepository.findAllInStore(storeId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public BranchResponse create(UUID storeId, BranchCreateRequest request) {
        Store store = requireStore(storeId);
        String name = request.name().trim();
        if (branchRepository.nameExists(storeId, name)) {
            throw new ConflictException("BRANCH_NAME_ALREADY_EXISTS", "Ya existe una sucursal con este nombre.");
        }

        State active = findState(StateCodes.ACTIVE);
        Location location = locationRepository.save(toLocation(request.location()));
        Branch branch = Branch.builder()
                .store(store)
                .name(name)
                .numberPhone(request.numberPhone().trim())
                .location(location)
                .state(active)
                .build();

        return toResponse(branchRepository.save(branch));
    }

    @Transactional
    public BranchResponse update(UUID storeId, UUID branchId, BranchUpdateRequest request) {
        requireStore(storeId);
        Branch branch = findBranch(storeId, branchId);

        if (request.name() != null) {
            String name = request.name().trim();
            if (branchRepository.nameUsedByOther(storeId, name, branchId)) {
                throw new ConflictException("BRANCH_NAME_ALREADY_EXISTS", "Ya existe una sucursal con este nombre.");
            }
            branch.setName(name);
        }
        if (request.numberPhone() != null) {
            branch.setNumberPhone(request.numberPhone().trim());
        }
        if (request.location() != null) {
            branch.setLocation(locationRepository.save(toLocation(request.location())));
        }

        return toResponse(branch);
    }

    @Transactional
    public void deactivate(UUID storeId, UUID branchId) {
        requireStore(storeId);
        Branch branch = findBranch(storeId, branchId);
        branch.setState(findState(StateCodes.INACTIVE));
    }

    @Transactional(readOnly = true)
    public long countActive(UUID storeId) {
        return branchRepository.countActiveInStore(storeId);
    }

    /** Exige una sucursal del almacén en estado {@code ACTIVE}; si no existe o no está activa, {@code BRANCH_NOT_FOUND}. */
    @Transactional(readOnly = true)
    public void requireActiveInStore(UUID storeId, UUID branchId) {
        Branch branch = findBranch(storeId, branchId);
        if (!StateCodes.ACTIVE.equals(branch.getState().getCode())) {
            throw new NotFoundException("BRANCH_NOT_FOUND", "No se encontró la sucursal activa.");
        }
    }

    private Store requireStore(UUID storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén."));
    }

    private Branch findBranch(UUID storeId, UUID branchId) {
        return branchRepository.findInStore(branchId, storeId)
                .orElseThrow(() -> new NotFoundException("BRANCH_NOT_FOUND", "No se encontró la sucursal."));
    }

    private State findState(String code) {
        return stateRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException(
                        "BRANCH_STATE_NOT_FOUND",
                        "No está configurado el estado " + code + " para las sucursales."
                ));
    }

    private Location toLocation(BranchLocationRequest request) {
        return Location.builder()
                .addressLine(request.addressLine().trim())
                .addressComplement(clean(request.addressComplement()))
                .city(request.city().trim())
                .department(request.department().trim())
                .country(request.country() == null || request.country().isBlank()
                        ? "Colombia"
                        : request.country().trim())
                .postalCode(clean(request.postalCode()))
                .latitude(request.latitude())
                .longitude(request.longitude())
                .provider("INTERNAL")
                .metadata(Map.of())
                .build();
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BranchResponse toResponse(Branch branch) {
        Location location = branch.getLocation();
        BranchLocationResponse locationResponse = new BranchLocationResponse(
                location.getId(),
                location.getAddressLine(),
                location.getAddressComplement(),
                location.getCity(),
                location.getDepartment(),
                location.getCountry(),
                location.getPostalCode(),
                location.getLatitude(),
                location.getLongitude()
        );
        return new BranchResponse(
                branch.getId(),
                branch.getStore().getId(),
                branch.getName(),
                branch.getNumberPhone(),
                branch.getState().getCode(),
                locationResponse
        );
    }
}
