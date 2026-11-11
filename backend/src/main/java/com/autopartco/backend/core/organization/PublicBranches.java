package com.autopartco.backend.core.organization;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.internal.Branch;
import com.autopartco.backend.core.organization.internal.BranchRepository;
import com.autopartco.backend.core.organization.internal.PublishedStoreAssets;
import com.autopartco.backend.shared.location.Location;

/** Sucursales activas de un almacén publicado, vistas por compradores. */
@Service
public class PublicBranches {
    static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final PublishedStoreAssets publishedStoreAssets;
    private final BranchRepository branchRepository;
    private final StoreServices storeServices;
    private final Clock clock;

    @Autowired
    public PublicBranches(
            PublishedStoreAssets publishedStoreAssets, BranchRepository branchRepository, StoreServices storeServices) {
        this(publishedStoreAssets, branchRepository, storeServices, Clock.system(BOGOTA));
    }

    /** {@code clock} fija la hora de {@code openNow} (la zona se toma del reloj). */
    public PublicBranches(
            PublishedStoreAssets publishedStoreAssets, BranchRepository branchRepository, StoreServices storeServices,
            Clock clock) {
        this.publishedStoreAssets = publishedStoreAssets;
        this.branchRepository = branchRepository;
        this.storeServices = storeServices;
        this.clock = clock.withZone(BOGOTA);
    }

    /**
     * Sucursales {@code ACTIVE} por nombre. {@code whatsapp} es solo el de la sucursal: el respaldo al del almacén y
     * {@code whatsappEnabled} los resuelve quien llama. 404 {@code STORE_NOT_FOUND} si el almacén no está publicado.
     */
    @Transactional(readOnly = true)
    public List<PublicBranch> list(UUID storeId) {
        publishedStoreAssets.requirePublished(storeId);
        Map<UUID, List<UUID>> servicesByBranch = storeServices.serviceIdsByBranch(storeId);
        return branchRepository.findActiveInStore(storeId).stream()
                .map(branch -> toPublic(branch, servicesByBranch.getOrDefault(branch.getId(), List.of())))
                .toList();
    }

    private PublicBranch toPublic(Branch branch, List<UUID> serviceIds) {
        Location location = branch.getLocation();
        return new PublicBranch(
                branch.getId(),
                branch.getName(),
                address(location),
                location.getCity(),
                location.getDepartment(),
                location.getLatitude(),
                location.getLongitude(),
                phone(branch.getNumberPhone()),
                branch.getWhatsapp(),
                branch.getOpeningHours(),
                OpeningHours.openNow(branch.getOpeningHours(), clock),
                serviceIds
        );
    }

    static String address(Location location) {
        String line = location.getAddressLine() == null ? "" : location.getAddressLine().trim();
        String complement = location.getAddressComplement();
        return complement == null || complement.isBlank() ? line : line + " " + complement.trim();
    }

    /** E.164 ({@code +57…}) si son 10 dígitos; si no, tal cual. */
    static String phone(String numberPhone) {
        if (numberPhone == null || numberPhone.isBlank()) {
            return null;
        }
        String phone = numberPhone.trim();
        return phone.matches("\\d{10}") ? "+57" + phone : phone;
    }

    /**
     * Sucursal pública. {@code openNow} es {@code null} sin horario semanal.
     *
     * @param address dirección más complemento
     * @param phone teléfono en E.164 cuando tiene 10 dígitos
     */
    public record PublicBranch(
            UUID id,
            String name,
            String address,
            String city,
            String department,
            BigDecimal latitude,
            BigDecimal longitude,
            String phone,
            String whatsapp,
            Map<String, Object> openingHours,
            Boolean openNow,
            List<UUID> serviceIds
    ) {
        public PublicBranch withWhatsapp(String value) {
            return new PublicBranch(id, name, address, city, department, latitude, longitude, phone, value,
                    openingHours, openNow, serviceIds);
        }
    }
}
