package com.autopartco.backend.core.organization.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.StoreApplicationStatus;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.persistence.StateCodes;

import lombok.AllArgsConstructor;

/**
 * Resuelve la ruta de un recurso de marca (logo o banner) de un almacén publicado (ver {@link #requirePublished}).
 * Separado de la descarga para no mantener la transacción abierta durante la
 * llamada al almacenamiento.
 */
@Component
@AllArgsConstructor
public class PublishedStoreAssets {

    private final StoreApplicationRepository applicationRepository;
    private final StoreRepository storeRepository;

    @Transactional(readOnly = true)
    public Optional<String> brandAssetPath(UUID storeId, String assetType) {
        Store store = requirePublished(storeId);
        String path = switch (assetType) {
            case "logo" -> store.getLogoPath();
            case "banner" -> store.getBannerPath();
            default -> null;
        };
        return Optional.ofNullable(path);
    }

    /**
     * Almacén publicado: en estado {@code ACTIVE} y, si tiene solicitud de vendedor, solicitud {@code ACTIVE} (los
     * almacenes anteriores al flujo de solicitudes no tienen una, como en {@code StoreAccess}). Si no,
     * {@code STORE_NOT_FOUND}.
     */
    @Transactional(readOnly = true)
    public Store requirePublished(UUID storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén."));
        boolean applicationBlocks = applicationRepository.findByStoreId(storeId)
                .map(application -> application.getStatus() != StoreApplicationStatus.ACTIVE)
                .orElse(false);
        if (applicationBlocks || !StateCodes.ACTIVE.equals(store.getState().getCode())) {
            throw new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén publicado.");
        }
        return store;
    }
}
