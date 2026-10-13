package com.autopartco.backend.seller.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.seller.dto.SellerRegistrationRequest;
import com.autopartco.backend.seller.dto.SellerRegistrationResponse;
import com.autopartco.backend.core.identity.AccountProvisioning;
import com.autopartco.backend.core.identity.PreparedRegistration;
import com.autopartco.backend.core.identity.internal.Account;
import com.autopartco.backend.core.identity.internal.AccountRepository;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.core.organization.internal.StoreApplication;
import com.autopartco.backend.core.organization.internal.StoreApplicationDocument;
import com.autopartco.backend.core.organization.StoreApplicationStatus;
import com.autopartco.backend.core.organization.ApplicationDocumentType;
import com.autopartco.backend.core.organization.LegalType;
import com.autopartco.backend.seller.entity.SellerPlan;
import com.autopartco.backend.seller.dto.SellerApplicationCreatedResponse;
import com.autopartco.backend.seller.dto.SellerApplicationRequest;
import com.autopartco.backend.core.organization.internal.StoreApplicationDocumentRepository;
import com.autopartco.backend.core.organization.internal.StoreApplicationRepository;
import com.autopartco.backend.core.organization.internal.StoreFileStorage;
import com.autopartco.backend.seller.repository.SellerPlanRepository;
import com.autopartco.backend.core.organization.internal.Store;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.core.organization.StoreService;

import lombok.extern.slf4j.Slf4j;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
@Slf4j
public class SellerRegistrationService {
    private final AccountProvisioning accountProvisioning;
    private final StoreService storeService;
    private final AccountRepository accountRepository;
    private final StoreRepository storeRepository;
    private final SellerPlanRepository planRepository;
    private final SellerPlanService planService;
    private final StoreApplicationRepository applicationRepository;
    private final StoreApplicationDocumentRepository documentRepository;
    private final StoreFileStorage fileService;

    @Transactional
    public SellerRegistrationResponse register(
            SellerRegistrationRequest request,
            Map<ApplicationDocumentType, MultipartFile> documents
    ) {
        validateDocuments(request.legalType(), documents);
        SellerPlan plan = resolvePlan(request.planId());
        PreparedRegistration prepared = accountProvisioning.registerSellerAccount(request.account());
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
            accountProvisioning.sendSellerVerification(prepared);
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
            Map<ApplicationDocumentType, MultipartFile> documents,
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
        if (applicationRepository.existsByApplicantAccountIdAndStatusIn(
                account.getId(),
                List.of(
                        StoreApplicationStatus.PENDING_REVIEW,
                        StoreApplicationStatus.CONFIGURATION_PENDING,
                        StoreApplicationStatus.ACTIVE
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
            com.autopartco.backend.core.organization.StoreRegistrationRequest storeRequest,
            LegalType legalType,
            SellerPlan plan,
            Map<ApplicationDocumentType, MultipartFile> documents
    ) {
        var storeResponse = storeService.register(storeRequest, account.getEmail());
        Store store = storeRepository.findById(storeResponse.id())
                .orElseThrow(() -> new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén creado."));
        StoreApplication application = applicationRepository.save(StoreApplication.builder()
                .applicantAccountId(account.getId())
                .store(store)
                .plan(plan)
                .selectedPlan(plan)
                .legalType(legalType)
                .status(StoreApplicationStatus.PENDING_REVIEW)
                .build());

        List<String> uploadedPaths = new java.util.ArrayList<>();
        try {
            for (Map.Entry<ApplicationDocumentType, MultipartFile> entry : documents.entrySet()) {
                StoreFileStorage.StoredFile file = fileService.uploadDocument(
                        application.getId(),
                        entry.getKey(),
                        entry.getValue()
                );
                uploadedPaths.add(file.path());
                documentRepository.save(StoreApplicationDocument.builder()
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
            com.autopartco.backend.core.organization.StoreResponse store,
            StoreApplication application,
            List<String> uploadedPaths
    ) {
    }

    private void validateDocuments(
            LegalType legalType,
            Map<ApplicationDocumentType, MultipartFile> documents
    ) {
        Set<ApplicationDocumentType> required = legalType == LegalType.NATURAL_PERSON
                ? EnumSet.of(ApplicationDocumentType.IDENTITY_DOCUMENT, ApplicationDocumentType.RUT)
                : EnumSet.of(
                        ApplicationDocumentType.RUT,
                        ApplicationDocumentType.LEGAL_REPRESENTATIVE_ID,
                        ApplicationDocumentType.CERTIFICATE_OF_EXISTENCE
                );
        if (documents == null
                || !documents.keySet().equals(required)
                || documents.values().stream().anyMatch(file -> file == null || file.isEmpty())) {
            throw new BadRequestException(
                    "SELLER_DOCUMENTS_INCOMPLETE",
                    legalType == LegalType.NATURAL_PERSON
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
