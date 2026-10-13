package com.autopartco.backend.seller.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountProvisioning;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.identity.LoginResponse;
import com.autopartco.backend.core.identity.SessionIssuer;
import com.autopartco.backend.shared.persistence.StateRepository;
import com.autopartco.backend.core.notification.EmailService;
import com.autopartco.backend.shared.config.AppProperties;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.seller.dto.SellerApplicationResponse;
import com.autopartco.backend.seller.dto.SellerApplicationReviewRequest;
import com.autopartco.backend.seller.dto.SellerPlanResponse;
import com.autopartco.backend.seller.dto.SellerSetupResponse;
import com.autopartco.backend.core.organization.internal.StoreApplication;
import com.autopartco.backend.core.organization.internal.StoreApplicationDocument;
import com.autopartco.backend.core.organization.StoreApplicationStatus;
import com.autopartco.backend.core.organization.internal.StoreApplicationDocumentRepository;
import com.autopartco.backend.core.organization.internal.StoreApplicationRepository;
import com.autopartco.backend.core.organization.internal.StoreFileStorage;
import com.autopartco.backend.core.organization.internal.StoreFileStorage.StoredFile;
import com.autopartco.backend.core.organization.internal.Store;
import com.autopartco.backend.shared.persistence.State;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class SellerApplicationService {
    private static final String ACTIVE_STATE = "ACTIVE";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StoreApplicationRepository applicationRepository;
    private final StoreApplicationDocumentRepository documentRepository;
    private final AccountLookup accountLookup;
    private final AccountProvisioning accountProvisioning;
    private final StateRepository stateRepository;
    private final SessionIssuer sessionIssuer;
    private final SellerPlanService planService;
    private final StoreFileStorage fileService;
    private final EmailService emailService;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public List<SellerApplicationResponse> pendingApplications() {
        // Como el antiguo join con la cuenta: se omiten las solicitudes cuyo solicitante ya no existe.
        return applicationRepository.findAllByStatusWithOwner(StoreApplicationStatus.PENDING_REVIEW)
                .stream()
                .flatMap(application -> accountLookup.findById(application.getApplicantAccountId())
                        .map(applicant -> toResponse(application, applicant))
                        .stream())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SellerApplicationResponse> myApplications(String email) {
        return accountLookup.findByEmail(email)
                .map(applicant -> applicationRepository.findByApplicant(applicant.accountId()).stream()
                        .map(application -> toResponse(application, applicant))
                        .toList())
                .orElse(List.of());
    }

    @Transactional
    public SellerApplicationResponse review(
            UUID applicationId,
            String reviewerEmail,
            SellerApplicationReviewRequest request
    ) {
        StoreApplication application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_APPLICATION_NOT_FOUND",
                        "No se encontró la solicitud de vendedor."
                ));
        if (application.getStatus() != StoreApplicationStatus.PENDING_REVIEW) {
            throw new ConflictException(
                    "SELLER_APPLICATION_ALREADY_REVIEWED",
                    "La solicitud ya fue revisada."
            );
        }
        application.setReviewNotes(cleanNotes(request.notes()));
        application.setReviewedByAccountId(accountLookup.findByEmail(reviewerEmail)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "No se encontró el revisor."))
                .accountId());
        AccountRef applicant = requireApplicant(application);
        if (!request.approved()) {
            application.setStatus(StoreApplicationStatus.REJECTED);
            return toResponse(application, applicant);
        }

        String rawToken = newOnboardingToken();
        application.setOnboardingTokenHash(hash(rawToken));
        application.setOnboardingTokenExpiresAt(LocalDateTime.now().plusHours(48));
        application.setStatus(StoreApplicationStatus.CONFIGURATION_PENDING);
        String setupLink = UriComponentsBuilder
                .fromUriString(appProperties.frontendBaseUrl().replaceAll("/+$", ""))
                .path("/seller/setup")
                .queryParam("token", rawToken)
                .build()
                .toUriString();
        emailService.sendSellerOnboarding(
                applicant.email(),
                applicant.givenName(),
                setupLink
        );
        return toResponse(application, applicant);
    }

    @Transactional
    public SellerSetupResponse completeSetup(
            String rawToken,
            String businessDescription,
            org.springframework.web.multipart.MultipartFile logo,
            org.springframework.web.multipart.MultipartFile banner,
            String ipAddress,
            String userAgent
    ) {
        StoreApplication application = findByOnboardingToken(rawToken);
        if (application.getStatus() != StoreApplicationStatus.CONFIGURATION_PENDING
                || application.getOnboardingTokenExpiresAt() == null
                || application.getOnboardingTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException(
                    "SELLER_SETUP_TOKEN_INVALID",
                    "El enlace de configuración venció o ya fue utilizado."
            );
        }
        if (!ACTIVE_STATE.equals(requireApplicant(application).stateCode())) {
            throw new ConflictException(
                    "SELLER_EMAIL_NOT_VERIFIED",
                    "Verifica tu correo antes de terminar la configuración del almacén."
            );
        }

        Store store = application.getStore();
        String newLogoPath = null;
        String newBannerPath = null;
        try {
            if (logo != null && !logo.isEmpty()) {
                StoredFile stored = fileService.uploadBrandAsset(store.getId(), "logo", logo);
                newLogoPath = stored.path();
            }
            if (banner != null && !banner.isEmpty()) {
                StoredFile stored = fileService.uploadBrandAsset(store.getId(), "banner", banner);
                newBannerPath = stored.path();
            }
            if (newLogoPath != null) {
                store.setLogoPath(newLogoPath);
            }
            if (newBannerPath != null) {
                store.setBannerPath(newBannerPath);
            }
            if (businessDescription != null) {
                Map<String, Object> configuration = new HashMap<>(store.getConfiguration());
                configuration.put("description", businessDescription.trim());
                store.setConfiguration(configuration);
            }
            store.setState(activeStoreState());
            application.setPlan(planService.trialPlan());
            LocalDateTime setupAt = LocalDateTime.now();
            application.setConfigurationCompletedAt(setupAt);
            application.setTrialStartsAt(setupAt);
            application.setTrialEndsAt(application.getTrialStartsAt().plusMonths(1));
            application.setStatus(StoreApplicationStatus.ACTIVE);
            application.setOnboardingTokenHash(null);
            application.setOnboardingTokenExpiresAt(null);
            UUID applicantId = application.getApplicantAccountId();
            accountProvisioning.promoteToStoreManager(applicantId);
            LoginResponse tokens = sessionIssuer.issue(applicantId, ipAddress, userAgent);
            return new SellerSetupResponse(
                    store.getId(),
                    application.getStatus().name(),
                    application.getTrialEndsAt(),
                    toPlanResponse(planService.recommendedPlan()),
                    tokens
            );
        } catch (RuntimeException exception) {
            cleanup(newLogoPath, exception);
            cleanup(newBannerPath, exception);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public StoreApplicationDocument requireDocument(
            UUID applicationId,
            UUID documentId,
            String email,
            boolean root
    ) {
        StoreApplicationDocument document = documentRepository.findWithApplicationById(documentId)
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_DOCUMENT_NOT_FOUND",
                        "No se encontró el documento."
                ));
        StoreApplication application = document.getApplication();
        if (!application.getId().equals(applicationId)) {
            throw new NotFoundException("SELLER_DOCUMENT_NOT_FOUND", "No se encontró el documento.");
        }
        if (!root && !requireApplicant(application).email().equalsIgnoreCase(email)) {
            throw new AccessDeniedException("No tienes acceso a este documento.");
        }
        return document;
    }

    private AccountRef requireApplicant(StoreApplication application) {
        return accountLookup.findById(application.getApplicantAccountId())
                .orElseThrow(() -> new NotFoundException(
                        "ACCOUNT_NOT_FOUND",
                        "No se encontró la cuenta del solicitante."
                ));
    }

    private StoreApplication findByOnboardingToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("SELLER_SETUP_TOKEN_INVALID", "El enlace de configuración no es válido.");
        }
        return applicationRepository.findByOnboardingTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new BadRequestException(
                        "SELLER_SETUP_TOKEN_INVALID",
                        "El enlace de configuración no es válido."
                ));
    }

    private State activeStoreState() {
        return stateRepository.findByCode(ACTIVE_STATE)
                .orElseThrow(() -> new NotFoundException(
                        "STORE_ACTIVE_STATE_NOT_FOUND",
                        "No está configurado el estado activo para los almacenes."
                ));
    }

    private SellerApplicationResponse toResponse(StoreApplication application, AccountRef applicant) {
        List<SellerApplicationResponse.Document> documents =
                documentRepository.findByApplication_IdOrderByDocumentType(application.getId())
                        .stream()
                        .map(document -> new SellerApplicationResponse.Document(
                                document.getId(),
                                document.getDocumentType(),
                                document.getOriginalFilename(),
                                document.getContentType(),
                                document.getSizeBytes()
                        ))
                        .toList();
        Store store = application.getStore();
        return new SellerApplicationResponse(
                application.getId(),
                store.getId(),
                store.getBusinessName(),
                store.getNit(),
                store.getBusinessEmail(),
                applicant.email(),
                application.getLegalType(),
                application.getStatus(),
                toPlanResponse(application.getPlan()),
                toPlanResponse(application.getSelectedPlan()),
                documents,
                application.getReviewNotes(),
                application.getCreatedAt()
        );
    }

    private SellerPlanResponse toPlanResponse(com.autopartco.backend.seller.entity.SellerPlan plan) {
        return new SellerPlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getMonthlyPriceCop(),
                plan.getCurrency(),
                List.copyOf(plan.getFeatures()),
                plan.isRecommended(),
                plan.isEnabled(),
                plan.getMaxBranches(),
                plan.getMaxProducts(),
                plan.getMaxUsers(),
                plan.getCapabilities() == null ? List.of() : List.copyOf(plan.getCapabilities())
        );
    }

    private String cleanNotes(String notes) {
        if (notes == null) {
            return null;
        }
        String cleaned = notes.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String newOnboardingToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void cleanup(String path, RuntimeException originalException) {
        if (path == null) {
            return;
        }
        try {
            fileService.delete(path);
        } catch (RuntimeException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }
}
