package com.autopartco.backend.store.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.auth.repository.StateRepository;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.store.dto.BranchCreateRequest;
import com.autopartco.backend.store.dto.BranchLocationRequest;
import com.autopartco.backend.store.dto.BranchLocationResponse;
import com.autopartco.backend.store.dto.BranchResponse;
import com.autopartco.backend.store.dto.BranchUpdateRequest;
import com.autopartco.backend.store.entity.Branch;
import com.autopartco.backend.store.entity.Location;
import com.autopartco.backend.store.entity.Store;
import com.autopartco.backend.store.repository.BranchRepository;
import com.autopartco.backend.store.repository.LocationRepository;
import com.autopartco.backend.store.repository.StoreRepository;
import com.autopartco.backend.seller.service.SellerPlanEntitlementService;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class BranchService {
    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";

    private final BranchRepository branchRepository;
    private final LocationRepository locationRepository;
    private final StoreRepository storeRepository;
    private final StoreAccessService storeAccessService;
    private final StateRepository stateRepository;
    private final SellerPlanEntitlementService entitlementService;

    @Transactional(readOnly = true)
    public List<BranchResponse> list(UUID storeId, String managerEmail) {
        requirePermission(storeId, managerEmail, StorePermissionCodes.BRANCH_READ);
        return branchRepository.findAllInStore(storeId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public BranchResponse create(UUID storeId, String managerEmail, BranchCreateRequest request) {
        Store store = requirePermission(storeId, managerEmail, StorePermissionCodes.BRANCH_CREATE);
        storeRepository.findByIdForUpdate(storeId)
                .orElseThrow(() -> new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén."));
        entitlementService.requireCapacity(storeId, SellerPlanEntitlementService.Capacity.BRANCHES, 1);
        String name = request.name().trim();
        if (branchRepository.nameExists(storeId, name)) {
            throw new ConflictException("BRANCH_NAME_ALREADY_EXISTS", "Ya existe una sucursal con este nombre.");
        }

        State active = findState(ACTIVE);
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
    public BranchResponse update(
            UUID storeId,
            UUID branchId,
            String managerEmail,
            BranchUpdateRequest request
    ) {
        requirePermission(storeId, managerEmail, StorePermissionCodes.BRANCH_UPDATE);
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
    public void deactivate(UUID storeId, UUID branchId, String managerEmail) {
        requirePermission(storeId, managerEmail, StorePermissionCodes.BRANCH_DELETE);
        Branch branch = findBranch(storeId, branchId);
        branch.setState(findState(INACTIVE));
    }

    private Store requirePermission(UUID storeId, String email, String permissionCode) {
        storeAccessService.requirePermission(email, storeId, permissionCode);
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