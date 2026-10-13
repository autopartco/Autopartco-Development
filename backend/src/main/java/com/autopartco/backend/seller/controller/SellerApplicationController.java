package com.autopartco.backend.seller.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.seller.dto.SellerApplicationResponse;
import com.autopartco.backend.seller.dto.SellerApplicationCreatedResponse;
import com.autopartco.backend.seller.dto.SellerApplicationRequest;
import com.autopartco.backend.seller.dto.SellerApplicationReviewRequest;
import com.autopartco.backend.seller.dto.SellerSetupResponse;
import com.autopartco.backend.seller.entity.SellerApplicationDocument;
import com.autopartco.backend.seller.service.SellerApplicationService;
import com.autopartco.backend.seller.service.SellerFileService;
import com.autopartco.backend.seller.service.SellerRegistrationService;
import com.autopartco.backend.seller.entity.SellerDocumentType;
import com.autopartco.backend.store.entity.Store;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(ApiPaths.BASE + "/seller-applications")
@AllArgsConstructor
public class SellerApplicationController {
    private final SellerApplicationService applicationService;
    private final SellerFileService fileService;
    private final SellerRegistrationService sellerRegistrationService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SellerApplicationCreatedResponse> applyForExistingCustomer(
            @Valid @RequestPart("request") SellerApplicationRequest request,
            @RequestPart(name = "identityDocument", required = false) MultipartFile identityDocument,
            @RequestPart(name = "rut", required = false) MultipartFile rut,
            @RequestPart(name = "legalRepresentativeId", required = false) MultipartFile legalRepresentativeId,
            @RequestPart(name = "certificateOfExistence", required = false) MultipartFile certificateOfExistence,
            Authentication authentication
    ) {
        java.util.Map<SellerDocumentType, MultipartFile> documents =
                new java.util.EnumMap<>(SellerDocumentType.class);
        if (identityDocument != null) {
            documents.put(SellerDocumentType.IDENTITY_DOCUMENT, identityDocument);
        }
        if (rut != null) {
            documents.put(SellerDocumentType.RUT, rut);
        }
        if (legalRepresentativeId != null) {
            documents.put(SellerDocumentType.LEGAL_REPRESENTATIVE_ID, legalRepresentativeId);
        }
        if (certificateOfExistence != null) {
            documents.put(SellerDocumentType.CERTIFICATE_OF_EXISTENCE, certificateOfExistence);
        }
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(sellerRegistrationService.applyForExistingCustomer(
                        request,
                        documents,
                        authentication.getName()
                ));
    }

    @GetMapping("/me")
    public List<SellerApplicationResponse> myApplications(Authentication authentication) {
        return applicationService.myApplications(authentication.getName());
    }

    @GetMapping("/root/pending")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public List<SellerApplicationResponse> pendingApplications() {
        return applicationService.pendingApplications();
    }

    @PatchMapping("/root/{applicationId}/review")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public SellerApplicationResponse review(
            @PathVariable UUID applicationId,
            @Valid @org.springframework.web.bind.annotation.RequestBody SellerApplicationReviewRequest request,
            Authentication authentication
    ) {
        return applicationService.review(applicationId, authentication.getName(), request);
    }

    @GetMapping("/{applicationId}/documents/{documentId}")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable UUID applicationId,
            @PathVariable UUID documentId,
            Authentication authentication
    ) {
        boolean root = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ROOT"::equals);
        SellerApplicationDocument document =
                applicationService.requireDocument(applicationId, documentId, authentication.getName(), root);
        var stored = fileService.download(document.getObjectPath());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.getOriginalFilename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(stored.content());
    }

    @PostMapping(value = "/setup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SellerSetupResponse completeSetup(
            @RequestParam String token,
            @RequestParam(required = false) String businessDescription,
            @RequestPart(name = "logo", required = false) MultipartFile logo,
            @RequestPart(name = "banner", required = false) MultipartFile banner,
            HttpServletRequest request
    ) {
        return applicationService.completeSetup(
                token,
                businessDescription,
                logo,
                banner,
                request.getRemoteAddr(),
                request.getHeader("User-Agent") == null ? "" : request.getHeader("User-Agent")
        );
    }

    @GetMapping("/stores/{storeId}/brand/{assetType}")
    public ResponseEntity<byte[]> publicBrandAsset(
            @PathVariable UUID storeId,
            @PathVariable String assetType
    ) {
        Store store = applicationService.requirePublicStore(storeId);
        String path = switch (assetType) {
            case "logo" -> store.getLogoPath();
            case "banner" -> store.getBannerPath();
            default -> null;
        };
        if (path == null) {
            return ResponseEntity.notFound().build();
        }
        var stored = fileService.download(path);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .body(stored.content());
    }
}
