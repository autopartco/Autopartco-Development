package com.autopartco.backend.core.organization;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.core.organization.internal.PublishedStoreAssets;
import com.autopartco.backend.core.organization.internal.StoreFileStorage;
import com.autopartco.backend.shared.storage.SupabaseStorageService.StoredObject;

import lombok.AllArgsConstructor;

/**
 * Archivos de almacén para otros módulos: recursos de marca públicos, subida de marca y descarga de
 * documentos de solicitud.
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

    /** Sube el logo o el banner del almacén y devuelve la ruta del objeto. */
    public String uploadBrandAsset(UUID storeId, String assetType, MultipartFile file) {
        return fileStorage.uploadBrandAsset(storeId, assetType, file).path();
    }

    /** Descarga un objeto del bucket de negocio (documentos de solicitud y marca). */
    public StoredObject download(String objectPath) {
        return fileStorage.download(objectPath);
    }

    public void delete(String objectPath) {
        fileStorage.delete(objectPath);
    }
}
