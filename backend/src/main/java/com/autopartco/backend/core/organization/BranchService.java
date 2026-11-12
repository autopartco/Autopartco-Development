package com.autopartco.backend.core.organization;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.internal.Branch;
import com.autopartco.backend.core.organization.internal.BranchRepository;
import com.autopartco.backend.core.organization.internal.Store;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.events.ListingsChanged;
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

    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-4.3");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("13.5");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-82");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("-66.8");

    private final BranchRepository branchRepository;
    private final LocationRepository locationRepository;
    private final StoreRepository storeRepository;
    private final StateRepository stateRepository;
    /** Publica {@link ListingsChanged}: {@code core.search} reindexa la sucursal (ubicación, estado) tras el commit. */
    private final ApplicationEventPublisher events;

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
        Location location = locationRepository.save(toLocation(request.location(), null));
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
            branch.setLocation(locationRepository.save(toLocation(request.location(), branch.getLocation())));
        }
        if (request.whatsapp() != null) {
            branch.setWhatsapp(clean(request.whatsapp()));
        }
        if (request.openingHours() != null) {
            branch.setOpeningHours(OpeningHours.validate(request.openingHours()));
        }

        events.publishEvent(ListingsChanged.ofBranch(branchId));
        return toResponse(branch);
    }

    @Transactional
    public void deactivate(UUID storeId, UUID branchId) {
        requireStore(storeId);
        Branch branch = findBranch(storeId, branchId);
        branch.setState(findState(StateCodes.INACTIVE));
        events.publishEvent(ListingsChanged.ofBranch(branchId));
    }

    @Transactional(readOnly = true)
    public long countActive(UUID storeId) {
        return branchRepository.countActiveInStore(storeId);
    }

    /** {@code true} si la sucursal es del almacén (en cualquier estado). */
    @Transactional(readOnly = true)
    public boolean isInStore(UUID storeId, UUID branchId) {
        return branchRepository.findInStore(branchId, storeId).isPresent();
    }

    /** Exige una sucursal del almacén en estado {@code ACTIVE}; si no existe o no está activa, {@code BRANCH_NOT_FOUND}. */
    @Transactional(readOnly = true)
    public void requireActiveInStore(UUID storeId, UUID branchId) {
        Branch branch = findBranch(storeId, branchId);
        if (!StateCodes.ACTIVE.equals(branch.getState().getCode())) {
            throw new NotFoundException("BRANCH_NOT_FOUND", "No se encontró la sucursal activa.");
        }
    }

    /** Exige que la sucursal sea del almacén (en cualquier estado); si no, {@code BRANCH_NOT_FOUND}. */
    @Transactional(readOnly = true)
    public void requireInStore(UUID storeId, UUID branchId) {
        findBranch(storeId, branchId);
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

    static final String APPROXIMATE = "approximate";

    /**
     * Toda sede necesita un punto para el mapa público. Si la petición no trae coordenadas se conservan las de
     * {@code previous} solo cuando la dirección no cambió (formularios que no editan el punto); si no, 400
     * {@code BRANCH_COORDINATES_REQUIRED}.
     */
    private Location toLocation(BranchLocationRequest request, Location previous) {
        requireColombianPoint(request.latitude(), request.longitude());
        String addressLine = request.addressLine().trim();
        String city = request.city().trim();
        String department = request.department().trim();
        BigDecimal latitude = request.latitude();
        BigDecimal longitude = request.longitude();
        boolean approximate = Boolean.TRUE.equals(request.approximate());
        if (latitude == null) {
            if (previous == null || previous.getLatitude() == null || previous.getLongitude() == null
                    || !sameAddress(previous, addressLine, city, department)) {
                throw new BadRequestException("BRANCH_COORDINATES_REQUIRED",
                        "Marca la ubicación de la sucursal en el mapa.");
            }
            latitude = previous.getLatitude();
            longitude = previous.getLongitude();
            approximate = isApproximate(previous);
        }
        return Location.builder()
                .addressLine(addressLine)
                .addressComplement(clean(request.addressComplement()))
                .city(city)
                .department(department)
                .country(request.country() == null || request.country().isBlank()
                        ? "Colombia"
                        : request.country().trim())
                .postalCode(clean(request.postalCode()))
                .latitude(latitude)
                .longitude(longitude)
                .provider("INTERNAL")
                .metadata(approximate ? Map.of(APPROXIMATE, true) : Map.of())
                .build();
    }

    private static boolean sameAddress(Location previous, String addressLine, String city, String department) {
        return addressLine.equalsIgnoreCase(trimmed(previous.getAddressLine()))
                && city.equalsIgnoreCase(trimmed(previous.getCity()))
                && department.equalsIgnoreCase(trimmed(previous.getDepartment()));
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    /** Punto marcado como aproximado en {@code locations.metadata}. */
    static boolean isApproximate(Location location) {
        return location.getMetadata() != null && Boolean.TRUE.equals(location.getMetadata().get(APPROXIMATE));
    }

    /** Ambas coordenadas o ninguna, dentro de Colombia. */
    static void requireColombianPoint(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null && longitude == null) {
            return;
        }
        if (latitude == null || longitude == null) {
            throw new BadRequestException("LOCATION_INCOMPLETE", "Indica latitud y longitud, o ninguna de las dos.");
        }
        if (latitude.compareTo(MIN_LATITUDE) < 0 || latitude.compareTo(MAX_LATITUDE) > 0
                || longitude.compareTo(MIN_LONGITUDE) < 0 || longitude.compareTo(MAX_LONGITUDE) > 0) {
            throw new BadRequestException("LOCATION_OUT_OF_RANGE", "El punto debe estar dentro de Colombia.");
        }
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
                location.getLongitude(),
                isApproximate(location)
        );
        return new BranchResponse(
                branch.getId(),
                branch.getStore().getId(),
                branch.getName(),
                branch.getNumberPhone(),
                branch.getState().getCode(),
                locationResponse,
                branch.getWhatsapp(),
                branch.getOpeningHours()
        );
    }
}
