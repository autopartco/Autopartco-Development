package com.autopartco.backend.seller.controller;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.seller.dto.SellerRegistrationRequest;
import com.autopartco.backend.seller.dto.SellerRegistrationResponse;
import com.autopartco.backend.seller.entity.SellerDocumentType;
import com.autopartco.backend.seller.service.SellerRegistrationService;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * Registro público de vendedor (cuenta nueva + almacén + solicitud). Temporal: conserva la ruta
 * {@code POST /auth/register/seller} hasta que la fase 6 lo mueva a {@code storesuite.onboarding}
 * ({@code POST /store-applications}).
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/auth")
@AllArgsConstructor
public class SellerRegistrationController {
    private final SellerRegistrationService sellerRegistrationService;

    @PostMapping(value = "/register/seller", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SellerRegistrationResponse> registerSeller(
            @Valid @RequestPart("request") SellerRegistrationRequest request,
            @RequestPart(name = "identityDocument", required = false) MultipartFile identityDocument,
            @RequestPart(name = "rut", required = false) MultipartFile rut,
            @RequestPart(name = "legalRepresentativeId", required = false) MultipartFile legalRepresentativeId,
            @RequestPart(name = "certificateOfExistence", required = false) MultipartFile certificateOfExistence
    ) {
        Map<SellerDocumentType, MultipartFile> documents = new EnumMap<>(SellerDocumentType.class);
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
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sellerRegistrationService.register(request, documents));
    }
}
