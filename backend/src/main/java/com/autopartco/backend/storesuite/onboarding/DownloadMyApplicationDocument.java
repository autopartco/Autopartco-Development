package com.autopartco.backend.storesuite.onboarding;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.autopartco.backend.core.organization.StoreApplications;
import com.autopartco.backend.core.organization.StoreFiles;
import com.autopartco.backend.shared.storage.SupabaseStorageService.StoredObject;

import lombok.AllArgsConstructor;

/**
 * Descarga de un documento de solicitud por su solicitante. Los metadatos se leen en una transacción de solo
 * lectura y la descarga del bucket se hace fuera de ella, como antes.
 */
@Service
@AllArgsConstructor
public class DownloadMyApplicationDocument {
    private final StoreApplications storeApplications;
    private final StoreFiles storeFiles;

    public record Download(StoreApplications.DocumentFile document, StoredObject stored) {
    }

    public Download execute(UUID applicationId, UUID documentId, String email) {
        StoreApplications.DocumentFile document = storeApplications.requireOwnDocument(applicationId, documentId, email);
        return new Download(document, storeFiles.download(document.objectPath()));
    }
}
