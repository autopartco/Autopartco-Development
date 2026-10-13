package com.autopartco.backend.core.organization;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.autopartco.backend.core.organization.internal.PublishedStoreAssets;
import com.autopartco.backend.core.organization.internal.StoreFileStorage;
import com.autopartco.backend.shared.storage.SupabaseStorageService.StoredObject;

import lombok.AllArgsConstructor;

/**
 * Archivos de almacén para otros módulos: recursos de marca públicos. Los documentos de solicitud y la subida
 * de marca se exponen aquí cuando migren sus casos de uso (fase 6).
 */
@Service
@AllArgsConstructor
public class StoreFiles {
    private final PublishedStoreAssets publishedStoreAssets;
    private final StoreFileStorage fileStorage;

    /**
     * Descarga el logo o el banner de un almacén publicado. Vacío si el tipo no existe o el almacén no lo tiene.
     *
     * @throws com.autopartco.backend.shared.error.NotFoundException {@code STORE_NOT_FOUND} si no está publicado
     */
    public Optional<StoredObject> publicBrandAsset(UUID storeId, String assetType) {
        return publishedStoreAssets.brandAssetPath(storeId, assetType).map(fileStorage::download);
    }
}
