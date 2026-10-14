package com.autopartco.backend.storesuite.onboarding;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.core.organization.ApplicationDocumentType;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * Onboarding de almacén: registro público de vendedor ({@code POST /store-applications}), configuración con el
 * token de onboarding ({@code POST /store-applications/setup}, público) y las solicitudes de la cuenta
 * autenticada ({@code /me/store-applications}).
 */
@RestController
@RequestMapping(ApiPaths.BASE)
@AllArgsConstructor
public class StoreApplicationController {
    private final SubmitStoreApplication submitStoreApplication;
    private final CompleteStoreSetup completeStoreSetup;
    private final ListMyApplications listMyApplications;
    private final DownloadMyApplicationDocument downloadMyApplicationDocument;

    @PostMapping(value = "/store-applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SellerSignupResponse> registerSeller(
            @Valid @RequestPart("request") SellerSignupRequest request,
            @RequestPart(name = "identityDocument", required = false) MultipartFile identityDocument,
            @RequestPart(name = "rut", required = false) MultipartFile rut,
            @RequestPart(name = "legalRepresentativeId", required = false) MultipartFile legalRepresentativeId,
            @RequestPart(name = "certificateOfExistence", required = false) MultipartFile certificateOfExistence
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(submitStoreApplication.register(
                        request,
                        documents(identityDocument, rut, legalRepresentativeId, certificateOfExistence)
                ));
    }

    @PostMapping(value = "/store-applications/setup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StoreSetupResponse completeSetup(
            @RequestParam String token,
            @RequestParam(required = false) String businessDescription,
            @RequestPart(name = "logo", required = false) MultipartFile logo,
            @RequestPart(name = "banner", required = false) MultipartFile banner,
            HttpServletRequest request
    ) {
        return completeStoreSetup.execute(
                token,
                businessDescription,
                logo,
                banner,
                request.getRemoteAddr(),
                request.getHeader("User-Agent") == null ? "" : request.getHeader("User-Agent")
        );
    }

    @PostMapping(value = "/me/store-applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoreApplicationCreatedResponse> applyForExistingCustomer(
            @Valid @RequestPart("request") StoreApplicationRequest request,
            @RequestPart(name = "identityDocument", required = false) MultipartFile identityDocument,
            @RequestPart(name = "rut", required = false) MultipartFile rut,
            @RequestPart(name = "legalRepresentativeId", required = false) MultipartFile legalRepresentativeId,
            @RequestPart(name = "certificateOfExistence", required = false) MultipartFile certificateOfExistence,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(submitStoreApplication.applyForExistingCustomer(
                        request,
                        documents(identityDocument, rut, legalRepresentativeId, certificateOfExistence),
                        authentication.getName()
                ));
    }

    @GetMapping("/me/store-applications")
    public List<StoreApplicationResponse> myApplications(Authentication authentication) {
        return listMyApplications.execute(authentication.getName());
    }

    @GetMapping("/me/store-applications/{applicationId}/documents/{documentId}")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable UUID applicationId,
            @PathVariable UUID documentId,
            Authentication authentication
    ) {
        DownloadMyApplicationDocument.Download download =
                downloadMyApplicationDocument.execute(applicationId, documentId, authentication.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.document().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.document().originalFilename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(download.stored().content());
    }

    private static Map<ApplicationDocumentType, MultipartFile> documents(
            MultipartFile identityDocument,
            MultipartFile rut,
            MultipartFile legalRepresentativeId,
            MultipartFile certificateOfExistence
    ) {
        Map<ApplicationDocumentType, MultipartFile> documents = new EnumMap<>(ApplicationDocumentType.class);
        if (identityDocument != null) {
            documents.put(ApplicationDocumentType.IDENTITY_DOCUMENT, identityDocument);
        }
        if (rut != null) {
            documents.put(ApplicationDocumentType.RUT, rut);
        }
        if (legalRepresentativeId != null) {
            documents.put(ApplicationDocumentType.LEGAL_REPRESENTATIVE_ID, legalRepresentativeId);
        }
        if (certificateOfExistence != null) {
            documents.put(ApplicationDocumentType.CERTIFICATE_OF_EXISTENCE, certificateOfExistence);
        }
        return documents;
    }
}
