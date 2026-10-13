package com.autopartco.backend.operations.storeverification;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.autopartco.backend.core.organization.StoreApplications;
import com.autopartco.backend.core.organization.StoreFiles;
import com.autopartco.backend.shared.storage.SupabaseStorageService.StoredObject;

import lombok.AllArgsConstructor;

/** Descarga de cualquier documento de solicitud por root (lo exige el controlador). */
@Service
@AllArgsConstructor
public class DownloadApplicationDocument {
    private final StoreApplications storeApplications;
    private final StoreFiles storeFiles;

    public record Download(StoreApplications.DocumentFile document, StoredObject stored) {
    }

    public Download execute(UUID applicationId, UUID documentId) {
        StoreApplications.DocumentFile document = storeApplications.requireDocument(applicationId, documentId);
        return new Download(document, storeFiles.download(document.objectPath()));
    }
}
