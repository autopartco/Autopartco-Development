package com.autopartco.backend.operations.storeverification;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.organization.StoreApplicationReviewRequest;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * Verificación de almacenes por root: solicitudes pendientes, revisión y descarga de documentos.
 */
@RestController
@RequestMapping(ApiPaths.ADMIN + "/store-applications")
@AllArgsConstructor
public class StoreVerificationController {
    private final ListPendingApplications listPendingApplications;
    private final ReviewStoreApplication reviewStoreApplication;
    private final DownloadApplicationDocument downloadApplicationDocument;

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public List<StoreApplicationResponse> pendingApplications() {
        return listPendingApplications.execute();
    }

    @PatchMapping("/{applicationId}/review")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public StoreApplicationResponse review(
            @PathVariable UUID applicationId,
            @Valid @RequestBody StoreApplicationReviewRequest request,
            Authentication authentication
    ) {
        return reviewStoreApplication.execute(applicationId, authentication.getName(), request);
    }

    @GetMapping("/{applicationId}/documents/{documentId}")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable UUID applicationId,
            @PathVariable UUID documentId
    ) {
        DownloadApplicationDocument.Download download = downloadApplicationDocument.execute(applicationId, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.document().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.document().originalFilename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(download.stored().content());
    }
}
