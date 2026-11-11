package com.autopartco.backend.core.organization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.internal.BranchRepository;
import com.autopartco.backend.core.organization.internal.OfferedService;
import com.autopartco.backend.core.organization.internal.OfferedServiceRepository;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/**
 * Servicios que presta un almacén (instalación, alineación, diagnóstico…), siempre acotados por {@code storeId}: un
 * servicio de otro almacén es {@code SERVICE_NOT_FOUND}. No resuelve permisos: lo hacen los casos de uso de
 * {@code storesuite.services}.
 */
@Service
@AllArgsConstructor
public class StoreServices {
    public static final int MAX_ACTIVE = 50;

    private final OfferedServiceRepository serviceRepository;
    private final BranchRepository branchRepository;
    private final StoreRepository storeRepository;

    @Transactional(readOnly = true)
    public List<StoreServiceView> list(UUID storeId, boolean includeInactive) {
        List<OfferedService> services = includeInactive
                ? serviceRepository.findAllInStore(storeId)
                : serviceRepository.findActiveInStore(storeId);
        return services.stream().map(StoreServices::toView).toList();
    }

    @Transactional(readOnly = true)
    public StoreServiceView get(UUID storeId, UUID serviceId) {
        return toView(find(storeId, serviceId));
    }

    /** Crea un servicio activo. Bloquea la fila del almacén para que dos altas simultáneas no superen el máximo. */
    @Transactional
    public StoreServiceView create(UUID storeId, StoreServiceRequest request) {
        lockStore(storeId);
        Set<UUID> branchIds = requireBranchesInStore(storeId, request.branchIds());
        requireCapacity(storeId);
        OfferedService service = new OfferedService();
        service.setStoreId(storeId);
        service.setActive(true);
        apply(service, request, branchIds);
        return toView(serviceRepository.saveAndFlush(service));
    }

    @Transactional
    public StoreServiceView update(UUID storeId, UUID serviceId, StoreServiceRequest request) {
        lockStore(storeId);
        OfferedService service = find(storeId, serviceId);
        if (request.version() != null && !request.version().equals(service.getVersion())) {
            throw new ConflictException("SERVICE_VERSION_CONFLICT",
                    "El servicio cambió mientras lo editabas. Recárgalo e inténtalo de nuevo.");
        }
        Set<UUID> branchIds = requireBranchesInStore(storeId, request.branchIds());
        if (request.active() != null && request.active() && !service.isActive()) {
            requireCapacity(storeId);
        }
        if (request.active() != null) {
            service.setActive(request.active());
        }
        apply(service, request, branchIds);
        return toView(serviceRepository.saveAndFlush(service));
    }

    /** Desactiva el servicio (idempotente). */
    @Transactional
    public void deactivate(UUID storeId, UUID serviceId) {
        find(storeId, serviceId).setActive(false);
    }

    /** Servicios activos para el perfil público: sucursales solo activas y orden {@code sort_order, name}. */
    @Transactional(readOnly = true)
    public List<PublicStoreService> publicServices(UUID storeId) {
        List<OfferedService> services = serviceRepository.findActiveInStore(storeId);
        if (services.isEmpty()) {
            return List.of();
        }
        Set<UUID> activeBranches = new HashSet<>(branchRepository.findActiveIdsInStore(storeId));
        return services.stream()
                .map(service -> new PublicStoreService(
                        service.getId(),
                        service.getCategory(),
                        service.getName(),
                        service.getDescription(),
                        service.getPriceFrom(),
                        service.getDurationMinutes(),
                        service.getBranchIds().stream().filter(activeBranches::contains).sorted().toList()))
                .toList();
    }

    /** Ids de servicios activos que presta cada sucursal. */
    @Transactional(readOnly = true)
    public Map<UUID, List<UUID>> serviceIdsByBranch(UUID storeId) {
        Map<UUID, List<UUID>> byBranch = new HashMap<>();
        for (Object[] link : serviceRepository.findActiveBranchLinks(storeId)) {
            byBranch.computeIfAbsent((UUID) link[1], ignored -> new ArrayList<>()).add((UUID) link[0]);
        }
        byBranch.values().forEach(ids -> ids.sort(null));
        return byBranch;
    }

    private void apply(OfferedService service, StoreServiceRequest request, Set<UUID> branchIds) {
        service.setCategory(request.category());
        service.setName(request.name().trim());
        service.setDescription(blankToNull(request.description()));
        service.setPriceFrom(request.priceFrom());
        service.setDurationMinutes(request.durationMinutes());
        if (request.sortOrder() != null) {
            service.setSortOrder(request.sortOrder());
        }
        service.getBranchIds().retainAll(branchIds);
        service.getBranchIds().addAll(branchIds);
    }

    private Set<UUID> requireBranchesInStore(UUID storeId, List<UUID> requested) {
        Set<UUID> branchIds = requested == null ? new HashSet<>() : new HashSet<>(requested);
        if (!branchIds.isEmpty() && !new HashSet<>(branchRepository.findIdsInStore(storeId)).containsAll(branchIds)) {
            throw new BadRequestException("BRANCH_NOT_IN_STORE", "Alguna sucursal no pertenece al almacén.");
        }
        return branchIds;
    }

    private void requireCapacity(UUID storeId) {
        if (serviceRepository.countActiveInStore(storeId) >= MAX_ACTIVE) {
            throw new ConflictException("SERVICE_LIMIT_REACHED",
                    "Un almacén puede tener hasta " + MAX_ACTIVE + " servicios activos.");
        }
    }

    private void lockStore(UUID storeId) {
        storeRepository.lockById(storeId)
                .orElseThrow(() -> new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén."));
    }

    private OfferedService find(UUID storeId, UUID serviceId) {
        return serviceRepository.findInStore(serviceId, storeId)
                .orElseThrow(() -> new NotFoundException("SERVICE_NOT_FOUND", "No se encontró el servicio."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static StoreServiceView toView(OfferedService service) {
        return new StoreServiceView(
                service.getId(),
                service.getCategory(),
                service.getName(),
                service.getDescription(),
                service.getPriceFrom(),
                service.getDurationMinutes(),
                service.isActive(),
                service.getSortOrder(),
                service.getBranchIds().stream().sorted().toList(),
                service.getVersion());
    }
}
