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
 * Resuelve la ruta de un recurso de marca (logo o banner) de un almacén publicado: solicitud {@code ACTIVE} y
 * almacén en estado {@code ACTIVE}. Separado de la descarga para no mantener la transacción abierta durante la
 * llamada al almacenamiento.
 */
@Component
@AllArgsConstructor
public class PublishedStoreAssets {

    private final StoreApplicationRepository applicationRepository;

    @Transactional(readOnly = true)
    public Optional<String> brandAssetPath(UUID storeId, String assetType) {
        StoreApplication application = applicationRepository.findByStoreId(storeId)
                .orElseThrow(() -> new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén."));
        if (application.getStatus() != StoreApplicationStatus.ACTIVE) {
            throw new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén publicado.");
        }
        Store store = application.getStore();
        if (!StateCodes.ACTIVE.equals(store.getState().getCode())) {
            throw new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén publicado.");
        }
        String path = switch (assetType) {
            case "logo" -> store.getLogoPath();
            case "banner" -> store.getBannerPath();
            default -> null;
        };
        return Optional.ofNullable(path);
    }
}
