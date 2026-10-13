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

import com.autopartco.backend.core.identity.AccountProvisioning;
import com.autopartco.backend.core.identity.LoginResponse;
import com.autopartco.backend.core.identity.SessionIssuer;
import com.autopartco.backend.core.identity.internal.AccountRepository;
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
import com.autopartco.backend.seller.entity.SellerApplication;
import com.autopartco.backend.seller.entity.SellerApplicationDocument;
import com.autopartco.backend.seller.entity.SellerApplicationStatus;
import com.autopartco.backend.seller.entity.SellerDocumentType;
import com.autopartco.backend.seller.repository.SellerApplicationDocumentRepository;
import com.autopartco.backend.seller.repository.SellerApplicationRepository;
import com.autopartco.backend.seller.service.SellerFileService.StoredFile;
import com.autopartco.backend.store.entity.Store;
import com.autopartco.backend.shared.persistence.State;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class SellerApplicationService {
    private static final String ACTIVE_STATE = "ACTIVE";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SellerApplicationRepository applicationRepository;
    private final SellerApplicationDocumentRepository documentRepository;
    private final AccountRepository accountRepository;
    private final AccountProvisioning accountProvisioning;
    private final StateRepository stateRepository;
    private final SessionIssuer sessionIssuer;
    private final SellerPlanService planService;
    private final SellerFileService fileService;
    private final EmailService emailService;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public List<SellerApplicationResponse> pendingApplications() {
        return applicationRepository.findAllByStatusWithOwner(SellerApplicationStatus.PENDING_REVIEW)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SellerApplicationResponse> myApplications(String email) {
        return applicationRepository.findByApplicantEmail(email).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SellerApplicationResponse review(
            UUID applicationId,
            String reviewerEmail,
            SellerApplicationReviewRequest request
    ) {
        SellerApplication application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_APPLICATION_NOT_FOUND",
                        "No se encontró la solicitud de vendedor."
                ));
        if (application.getStatus() != SellerApplicationStatus.PENDING_REVIEW) {
            throw new ConflictException(
                    "SELLER_APPLICATION_ALREADY_REVIEWED",
                    "La solicitud ya fue revisada."
            );
        }
        application.setReviewNotes(cleanNotes(request.notes()));
        application.setReviewedBy(accountRepository.findByEmailIgnoreCase(reviewerEmail)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "No se encontró el revisor.")));
        if (!request.approved()) {
            application.setStatus(SellerApplicationStatus.REJECTED);
            return toResponse(application);
        }

        String rawToken = newOnboardingToken();
        application.setOnboardingTokenHash(hash(rawToken));
        application.setOnboardingTokenExpiresAt(LocalDateTime.now().plusHours(48));
        application.setStatus(SellerApplicationStatus.CONFIGURATION_PENDING);
        String setupLink = UriComponentsBuilder
                .fromUriString(appProperties.frontendBaseUrl().replaceAll("/+$", ""))
                .path("/seller/setup")
                .queryParam("token", rawToken)
                .build()
                .toUriString();
        emailService.sendSellerOnboarding(
                application.getApplicant().getEmail(),
                application.getApplicant().getPerson().getName(),
                setupLink
        );
        return toResponse(application);
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
        SellerApplication application = findByOnboardingToken(rawToken);
        if (application.getStatus() != SellerApplicationStatus.CONFIGURATION_PENDING
                || application.getOnboardingTokenExpiresAt() == null
                || application.getOnboardingTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException(
                    "SELLER_SETUP_TOKEN_INVALID",
                    "El enlace de configuración venció o ya fue utilizado."
            );
        }
        if (!ACTIVE_STATE.equals(application.getApplicant().getState().getCode())) {
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
            application.setStatus(SellerApplicationStatus.ACTIVE);
            application.setOnboardingTokenHash(null);
            application.setOnboardingTokenExpiresAt(null);
            UUID applicantId = application.getApplicant().getId();
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
    public SellerApplicationDocument requireDocument(
            UUID applicationId,
            UUID documentId,
            String email,
            boolean root
    ) {
        SellerApplicationDocument document = documentRepository.findWithApplicationById(documentId)
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_DOCUMENT_NOT_FOUND",
                        "No se encontró el documento."
                ));
        SellerApplication application = document.getApplication();
        if (!application.getId().equals(applicationId)) {
            throw new NotFoundException("SELLER_DOCUMENT_NOT_FOUND", "No se encontró el documento.");
        }
        if (!root && !application.getApplicant().getEmail().equalsIgnoreCase(email)) {
            throw new AccessDeniedException("No tienes acceso a este documento.");
        }
        return document;
    }

    @Transactional(readOnly = true)
    public Store requirePublicStore(UUID storeId) {
        SellerApplication application = applicationRepository.findByStoreId(storeId)
                .orElseThrow(() -> new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén."));
        if (application.getStatus() != SellerApplicationStatus.ACTIVE) {
            throw new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén publicado.");
        }
        Store store = application.getStore();
        if (!ACTIVE_STATE.equals(store.getState().getCode())) {
            throw new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén publicado.");
        }
        return store;
    }

    private SellerApplication findByOnboardingToken(String rawToken) {
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

    private SellerApplicationResponse toResponse(SellerApplication application) {
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
                application.getApplicant().getEmail(),
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
