package com.autopartco.backend.auth.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.auth.dto.SellerRegistrationRequest;
import com.autopartco.backend.auth.dto.SellerRegistrationResponse;
import com.autopartco.backend.auth.entity.Account;
import com.autopartco.backend.auth.repository.AccountRepository;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.seller.entity.SellerApplication;
import com.autopartco.backend.seller.entity.SellerApplicationDocument;
import com.autopartco.backend.seller.entity.SellerApplicationStatus;
import com.autopartco.backend.seller.entity.SellerDocumentType;
import com.autopartco.backend.seller.entity.SellerLegalType;
import com.autopartco.backend.seller.entity.SellerPlan;
import com.autopartco.backend.seller.dto.SellerApplicationCreatedResponse;
import com.autopartco.backend.seller.dto.SellerApplicationRequest;
import com.autopartco.backend.seller.repository.SellerApplicationDocumentRepository;
import com.autopartco.backend.seller.repository.SellerApplicationRepository;
import com.autopartco.backend.seller.repository.SellerPlanRepository;
import com.autopartco.backend.seller.service.SellerFileService;
import com.autopartco.backend.seller.service.SellerPlanService;
import com.autopartco.backend.store.entity.Store;
import com.autopartco.backend.store.repository.StoreRepository;
import com.autopartco.backend.store.service.StoreService;

import lombok.extern.slf4j.Slf4j;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
@Slf4j
public class SellerRegistrationService {
    private final RegistrationService registrationService;
    private final StoreService storeService;
    private final AccountRepository accountRepository;
    private final StoreRepository storeRepository;
    private final SellerPlanRepository planRepository;
    private final SellerPlanService planService;
    private final SellerApplicationRepository applicationRepository;
    private final SellerApplicationDocumentRepository documentRepository;
    private final SellerFileService fileService;

    @Transactional
    public SellerRegistrationResponse register(
            SellerRegistrationRequest request,
            Map<SellerDocumentType, MultipartFile> documents
    ) {
        validateDocuments(request.legalType(), documents);
        SellerPlan plan = resolvePlan(request.planId());
        RegistrationService.PreparedRegistration prepared =
                registrationService.registerSellerAccount(request.account());
        Account account = accountRepository.findByEmailIgnoreCase(prepared.response().email())
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_ACCOUNT_NOT_FOUND",
                        "No se encontró la cuenta recién registrada."
                ));
        CreatedApplication created = createApplication(
                account,
                request.store(),
                request.legalType(),
                plan,
                documents
        );
        try {
            registrationService.sendSellerVerification(prepared);
        } catch (RuntimeException exception) {
            cleanupUploadedFiles(created.uploadedPaths(), exception);
            throw exception;
        }

        return new SellerRegistrationResponse(
                prepared.response(),
                created.store(),
                created.application().getId(),
                created.application().getStatus(),
                plan.getCode()
        );
    }

    @Transactional
    public SellerApplicationCreatedResponse applyForExistingCustomer(
            SellerApplicationRequest request,
            Map<SellerDocumentType, MultipartFile> documents,
            String email
    ) {
        validateDocuments(request.legalType(), documents);
        Account account = accountRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "No se encontró la cuenta autenticada."));
        if (!"ACTIVE".equals(account.getState().getCode())) {
            throw new BadRequestException("SELLER_EMAIL_NOT_VERIFIED", "Verifica tu correo antes de solicitar ser vendedor.");
        }
        if (!"CUSTOMER".equals(account.getAccountType().getCode())) {
            throw new BadRequestException("SELLER_APPLICATION_CUSTOMER_ONLY", "Solo una cuenta de cliente puede solicitar ser vendedor.");
        }
        if (applicationRepository.existsByApplicant_IdAndStatusIn(
                account.getId(),
                List.of(
                        SellerApplicationStatus.PENDING_REVIEW,
                        SellerApplicationStatus.CONFIGURATION_PENDING,
                        SellerApplicationStatus.ACTIVE
                )
        )) {
            throw new ConflictException(
                    "SELLER_APPLICATION_ALREADY_EXISTS",
                    "Ya tienes una solicitud de vendedor en curso."
            );
        }
        SellerPlan plan = resolvePlan(request.planId());
        CreatedApplication created = createApplication(
                account,
                request.store(),
                request.legalType(),
                plan,
                documents
        );
        return new SellerApplicationCreatedResponse(
                created.application().getId(),
                created.store().id(),
                created.application().getStatus(),
                plan.getCode()
        );
    }

    private SellerPlan resolvePlan(java.util.UUID planId) {
        return planId == null
                ? planService.trialPlan()
                : planRepository.findById(planId)
                        .filter(SellerPlan::isEnabled)
                        .orElseThrow(() -> new NotFoundException(
                                "SELLER_PLAN_NOT_FOUND",
                                "El plan seleccionado no existe o no está disponible."
                        ));
    }

    private CreatedApplication createApplication(
            Account account,
            com.autopartco.backend.store.dto.StoreRegistrationRequest storeRequest,
            SellerLegalType legalType,
            SellerPlan plan,
            Map<SellerDocumentType, MultipartFile> documents
    ) {
        var storeResponse = storeService.register(storeRequest, account.getEmail());
        Store store = storeRepository.findById(storeResponse.id())
                .orElseThrow(() -> new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén creado."));
        SellerApplication application = applicationRepository.save(SellerApplication.builder()
                .applicant(account)
                .store(store)
                .plan(plan)
                .selectedPlan(plan)
                .legalType(legalType)
                .status(SellerApplicationStatus.PENDING_REVIEW)
                .build());

        List<String> uploadedPaths = new java.util.ArrayList<>();
        try {
            for (Map.Entry<SellerDocumentType, MultipartFile> entry : documents.entrySet()) {
                SellerFileService.StoredFile file = fileService.uploadDocument(
                        application.getId(),
                        entry.getKey(),
                        entry.getValue()
                );
                uploadedPaths.add(file.path());
                documentRepository.save(SellerApplicationDocument.builder()
                        .application(application)
                        .documentType(entry.getKey())
                        .objectPath(file.path())
                        .originalFilename(file.originalFilename())
                        .contentType(file.contentType())
                        .sizeBytes(file.sizeBytes())
                        .build());
            }
            return new CreatedApplication(storeResponse, application, uploadedPaths);
        } catch (RuntimeException exception) {
            cleanupUploadedFiles(uploadedPaths, exception);
            throw exception;
        }
    }

    private record CreatedApplication(
            com.autopartco.backend.store.dto.StoreResponse store,
            SellerApplication application,
            List<String> uploadedPaths
    ) {
    }

    private void validateDocuments(
            SellerLegalType legalType,
            Map<SellerDocumentType, MultipartFile> documents
    ) {
        Set<SellerDocumentType> required = legalType == SellerLegalType.NATURAL_PERSON
                ? EnumSet.of(SellerDocumentType.IDENTITY_DOCUMENT, SellerDocumentType.RUT)
                : EnumSet.of(
                        SellerDocumentType.RUT,
                        SellerDocumentType.LEGAL_REPRESENTATIVE_ID,
                        SellerDocumentType.CERTIFICATE_OF_EXISTENCE
                );
        if (documents == null
                || !documents.keySet().equals(required)
                || documents.values().stream().anyMatch(file -> file == null || file.isEmpty())) {
            throw new BadRequestException(
                    "SELLER_DOCUMENTS_INCOMPLETE",
                    legalType == SellerLegalType.NATURAL_PERSON
                            ? "Adjunta la cédula y el RUT."
                            : "Adjunta el RUT, el certificado de existencia y representación legal y la cédula del representante."
            );
        }
    }

    private void cleanupUploadedFiles(List<String> paths, RuntimeException originalException) {
        for (String path : paths) {
            try {
                fileService.delete(path);
            } catch (RuntimeException cleanupException) {
                originalException.addSuppressed(cleanupException);
                log.error("Could not clean up seller application object {}", path, cleanupException);
            }
        }
    }
}
