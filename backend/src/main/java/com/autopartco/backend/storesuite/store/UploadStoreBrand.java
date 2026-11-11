package com.autopartco.backend.storesuite.store;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StoreBrand;
import com.autopartco.backend.core.organization.StoreFiles;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreProfileResponse;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.shared.error.PayloadTooLargeException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.events.StoreProfileChanged;

import lombok.AllArgsConstructor;

/**
 * Sube o quita el logo o el banner del almacén. Requiere {@code STORE_UPDATE}. Sin transacción propia: el archivo se
 * sube antes de guardar la ruta, y si guardar falla se borra; el archivo anterior se borra después (best-effort).
 */
@Service
@AllArgsConstructor
public class UploadStoreBrand {
    static final long MAX_LOGO_BYTES = 2L * 1024 * 1024;
    static final long MAX_BANNER_BYTES = 4L * 1024 * 1024;

    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final StoreFiles storeFiles;
    /** {@link StoreProfileChanged}: el perfil público cacheado expone la ruta del logo y del banner. */
    private final ApplicationEventPublisher events;

    public StoreProfileResponse upload(UUID storeId, String email, String assetType, MultipartFile file) {
        requireType(assetType);
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_UPDATE);
        long max = StoreBrand.LOGO.equals(assetType) ? MAX_LOGO_BYTES : MAX_BANNER_BYTES;
        if (file != null && file.getSize() > max) {
            throw new PayloadTooLargeException("BRAND_IMAGE_TOO_LARGE", StoreBrand.LOGO.equals(assetType)
                    ? "El logo no puede superar 2 MB."
                    : "El banner no puede superar 4 MB.");
        }
        String path = storeFiles.uploadBrandAsset(storeId, assetType, file);
        String previous;
        try {
            previous = storeService.replaceBrandAsset(storeId, assetType, path);
        } catch (RuntimeException failure) {
            deleteQuietly(path);
            throw failure;
        }
        deleteQuietly(previous);
        events.publishEvent(new StoreProfileChanged(storeId));
        return storeService.profile(storeId);
    }

    public void remove(UUID storeId, String email, String assetType) {
        requireType(assetType);
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_UPDATE);
        deleteQuietly(storeService.replaceBrandAsset(storeId, assetType, null));
        events.publishEvent(new StoreProfileChanged(storeId));
    }

    private static void requireType(String assetType) {
        if (!StoreBrand.TYPES.contains(assetType)) {
            throw new NotFoundException("BRAND_ASSET_TYPE_NOT_FOUND", "Tipo de imagen no válido.");
        }
    }

    private void deleteQuietly(String path) {
        if (path == null) {
            return;
        }
        try {
            storeFiles.delete(path);
        } catch (RuntimeException ignored) {
            // Best-effort: un archivo huérfano en el bucket no afecta al perfil.
        }
    }
}
