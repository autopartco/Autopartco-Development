package com.autopartco.backend.core.organization;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.organization.internal.Store;
import com.autopartco.backend.core.organization.internal.StoreApplication;
import com.autopartco.backend.core.organization.internal.StoreApplicationDocument;
import com.autopartco.backend.core.organization.internal.StoreApplicationDocumentRepository;
import com.autopartco.backend.core.organization.internal.StoreApplicationRepository;
import com.autopartco.backend.core.organization.internal.StoreFileStorage;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.error.TooManyRequestsException;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.persistence.StateCodes;
import com.autopartco.backend.shared.persistence.StateRepository;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fachada de las solicitudes de almacén (vendedor): alta con documentos, revisión y cierre
 * de la configuración. No resuelve permisos de usuario ni planes: lo hacen los casos de uso de los productos.
 */
@Service
@AllArgsConstructor
@Slf4j
public class StoreApplications {
    private static final Duration SETUP_LINK_COOLDOWN = Duration.ofMinutes(5);
    private static final int MAX_COOLDOWN_ENTRIES = 1000;

    private final StoreApplicationRepository applicationRepository;
    private final StoreApplicationDocumentRepository documentRepository;
    private final StoreRepository storeRepository;
    private final StoreService storeService;
    private final StoreFileStorage fileService;
    private final AccountLookup accountLookup;
    private final StateRepository stateRepository;
    private final Map<UUID, Instant> lastSetupLinkAt = new ConcurrentHashMap<>();

    /** Almacén y solicitud recién creados, con las rutas subidas para limpiarlas si el llamador falla después. */
    public record Submitted(
            StoreResponse store,
            UUID applicationId,
            StoreApplicationStatus status,
            List<String> uploadedPaths
    ) {
    }

    /** Resultado de una revisión; {@code approved} indica si hay que enviar el correo de configuración. */
    public record Reviewed(
            StoreApplicationView application,
            String applicantEmail,
            String applicantGivenName,
            boolean approved
    ) {
    }

    /** Solicitud bloqueada, de la cuenta autenticada, pendiente de configuración y con el correo verificado. */
    public record PendingSetup(UUID applicationId, UUID storeId, UUID applicantAccountId) {
    }

    /** Metadatos de un documento para descargarlo con {@link StoreFiles#download}. */
    public record DocumentFile(String objectPath, String originalFilename, String contentType) {
    }

    /** Resumen de la solicitud del almacén; si no existe, {@code SELLER_APPLICATION_NOT_FOUND}. */
    @Transactional(readOnly = true)
    public StoreApplicationSummary summaryOf(UUID storeId) {
        StoreApplication application = applicationRepository.findByStoreId(storeId)
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_APPLICATION_NOT_FOUND",
                        "No se encontró la suscripción de vendedor para este almacén."
                ));
        return new StoreApplicationSummary(
                application.getStore().getBusinessName(),
                application.getStore().getState().getCode(),
                application.getConfigurationCompletedAt() == null
        );
    }

    /**
     * @throws ConflictException {@code SELLER_APPLICATION_ALREADY_EXISTS} si el solicitante tiene una solicitud
     *         pendiente de revisión, de configuración o activa
     */
    @Transactional(readOnly = true)
    public void requireNoOpenApplication(UUID applicantAccountId) {
        if (applicationRepository.existsByApplicantAccountIdAndStatusIn(
                applicantAccountId,
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
    }

    /**
     * Registra el almacén (el solicitante queda como propietario), crea la solicitud {@code PENDING_REVIEW} y sube
     * sus documentos. Si una subida falla, borra las ya subidas y relanza.
     */
    @Transactional
    public Submitted submit(
            UUID applicantAccountId,
            String applicantEmail,
            StoreRegistrationRequest storeRequest,
            LegalType legalType,
            UUID planId,
            Map<ApplicationDocumentType, MultipartFile> documents
    ) {
        var storeResponse = storeService.register(storeRequest, applicantEmail);
        Store store = storeRepository.findById(storeResponse.id())
                .orElseThrow(() -> new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén creado."));
        StoreApplication application = applicationRepository.save(StoreApplication.builder()
                .applicantAccountId(applicantAccountId)
                .store(store)
                .planId(planId)
                .selectedPlanId(planId)
                .legalType(legalType)
                .status(StoreApplicationStatus.PENDING_REVIEW)
                .build());

        List<String> uploadedPaths = new ArrayList<>();
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
            return new Submitted(storeResponse, application.getId(), application.getStatus(), uploadedPaths);
        } catch (RuntimeException exception) {
            discardUploadedFiles(uploadedPaths, exception);
            throw exception;
        }
    }

    /** Borra los documentos subidos; los fallos se adjuntan como suprimidos a {@code originalException}. */
    public void discardUploadedFiles(List<String> paths, RuntimeException originalException) {
        for (String path : paths) {
            try {
                fileService.delete(path);
            } catch (RuntimeException cleanupException) {
                originalException.addSuppressed(cleanupException);
                log.error("Could not clean up seller application object {}", path, cleanupException);
            }
        }
    }

    /** Solicitudes pendientes de revisión, de la más antigua a la más reciente. */
    @Transactional(readOnly = true)
    public List<StoreApplicationView> pending() {
        // Como el antiguo join con la cuenta: se omiten las solicitudes cuyo solicitante ya no existe.
        return applicationRepository.findAllByStatusWithOwner(StoreApplicationStatus.PENDING_REVIEW)
                .stream()
                .flatMap(application -> accountLookup.findById(application.getApplicantAccountId())
                        .map(applicant -> toView(application, applicant))
                        .stream())
                .toList();
    }

    /** Solicitudes de la cuenta con ese correo, de la más reciente a la más antigua. Vacío si no existe. */
    @Transactional(readOnly = true)
    public List<StoreApplicationView> ofApplicant(String email) {
        return accountLookup.findByEmail(email)
                .map(applicant -> applicationRepository.findByApplicant(applicant.accountId()).stream()
                        .map(application -> toView(application, applicant))
                        .toList())
                .orElse(List.of());
    }

    /**
     * Rechaza o aprueba una solicitud pendiente. Al aprobar pasa la solicitud a {@code CONFIGURATION_PENDING}; no
     * genera token: la configuración la hace la dueña con su sesión.
     */
    @Transactional
    public Reviewed review(UUID applicationId, String reviewerEmail, StoreApplicationReviewRequest request) {
        StoreApplication application = applicationRepository.lockById(applicationId)
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
            return new Reviewed(toView(application, applicant), applicant.email(), applicant.givenName(), false);
        }

        application.setStatus(StoreApplicationStatus.CONFIGURATION_PENDING);
        return new Reviewed(toView(application, applicant), applicant.email(), applicant.givenName(), true);
    }

    /**
     * Bloquea la solicitud si es de la cuenta con ese correo y sigue en {@code CONFIGURATION_PENDING}.
     *
     * @throws NotFoundException {@code SELLER_APPLICATION_NOT_FOUND} (también si es de otra cuenta)
     * @throws ConflictException {@code SELLER_SETUP_NOT_PENDING}, {@code SELLER_EMAIL_NOT_VERIFIED}
     */
    @Transactional
    public PendingSetup lockForOwnerSetup(UUID applicationId, String email) {
        StoreApplication application = lockOwned(applicationId, email);
        requirePendingConfiguration(application);
        if (!StateCodes.ACTIVE.equals(requireApplicant(application).stateCode())) {
            throw new ConflictException(
                    "SELLER_EMAIL_NOT_VERIFIED",
                    "Verifica tu correo antes de terminar la configuración del almacén."
            );
        }
        return new PendingSetup(
                application.getId(),
                application.getStore().getId(),
                application.getApplicantAccountId()
        );
    }

    /** Destinatario del enlace de configuración reenviado (el enlace no lleva token). */
    public record SetupLink(String applicantEmail, String applicantGivenName) {
    }

    /**
     * Valida que la solicitud sea de la cuenta con ese correo y siga pendiente de configuración, y aplica el
     * enfriamiento. El enfriamiento es en memoria (por solicitud, acotado): no hay columna de último envío y no se
     * crean migraciones; se pierde al reiniciar y no se comparte entre instancias.
     *
     * @throws NotFoundException {@code SELLER_APPLICATION_NOT_FOUND} (también si es de otra cuenta)
     * @throws ConflictException {@code SELLER_SETUP_NOT_PENDING}
     * @throws TooManyRequestsException {@code SELLER_SETUP_LINK_COOLDOWN}
     */
    @Transactional
    public SetupLink requestSetupLinkForOwner(UUID applicationId, String email) {
        StoreApplication application = lockOwned(applicationId, email);
        requirePendingConfiguration(application);
        requireCooldownElapsed(applicationId);
        AccountRef applicant = requireApplicant(application);
        return new SetupLink(applicant.email(), applicant.givenName());
    }

    private StoreApplication lockOwned(UUID applicationId, String email) {
        StoreApplication application = applicationRepository.lockById(applicationId)
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_APPLICATION_NOT_FOUND",
                        "No se encontró la solicitud de vendedor."
                ));
        boolean owner = accountLookup.findById(application.getApplicantAccountId())
                .map(applicant -> applicant.email().equalsIgnoreCase(email))
                .orElse(false);
        if (!owner) {
            throw new NotFoundException("SELLER_APPLICATION_NOT_FOUND", "No se encontró la solicitud de vendedor.");
        }
        return application;
    }

    private void requirePendingConfiguration(StoreApplication application) {
        if (application.getStatus() != StoreApplicationStatus.CONFIGURATION_PENDING) {
            throw new ConflictException(
                    "SELLER_SETUP_NOT_PENDING",
                    "La solicitud no está pendiente de configuración."
            );
        }
    }

    private void requireCooldownElapsed(UUID applicationId) {
        Instant now = Instant.now();
        if (lastSetupLinkAt.size() >= MAX_COOLDOWN_ENTRIES) {
            lastSetupLinkAt.values().removeIf(sentAt -> sentAt.plus(SETUP_LINK_COOLDOWN).isBefore(now));
        }
        Instant previous = lastSetupLinkAt.get(applicationId);
        if (previous != null && now.isBefore(previous.plus(SETUP_LINK_COOLDOWN))) {
            throw new TooManyRequestsException(
                    "SELLER_SETUP_LINK_COOLDOWN",
                    "Ya enviamos un enlace hace poco. Espera unos minutos antes de pedir otro."
            );
        }
        lastSetupLinkAt.put(applicationId, now);
    }

    /**
     * Aplica la marca ya subida (rutas no nulas), la descripción (si viene) y activa el almacén.
     */
    @Transactional
    public void configureStore(UUID storeId, String logoPath, String bannerPath, String businessDescription) {
        Store store = storeRepository.getReferenceById(storeId);
        if (logoPath != null) {
            store.setLogoPath(logoPath);
        }
        if (bannerPath != null) {
            store.setBannerPath(bannerPath);
        }
        if (businessDescription != null) {
            Map<String, Object> configuration = new HashMap<>(store.getConfiguration());
            configuration.put("description", businessDescription.trim());
            store.setConfiguration(configuration);
        }
        store.setState(activeStoreState());
    }

    /** Cierra la configuración: solicitud {@code ACTIVE} (y limpia cualquier token de onboarding heredado). */
    @Transactional
    public StoreApplicationStatus completeConfiguration(UUID applicationId, LocalDateTime completedAt) {
        StoreApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_APPLICATION_NOT_FOUND",
                        "No se encontró la solicitud de vendedor."
                ));
        application.setConfigurationCompletedAt(completedAt);
        application.setStatus(StoreApplicationStatus.ACTIVE);
        application.setOnboardingTokenHash(null);
        application.setOnboardingTokenExpiresAt(null);
        return application.getStatus();
    }

    /** Documento de la solicitud para root. */
    @Transactional(readOnly = true)
    public DocumentFile requireDocument(UUID applicationId, UUID documentId) {
        return toFile(findDocument(applicationId, documentId));
    }

    /**
     * Documento de la solicitud para su solicitante.
     *
     * @throws AccessDeniedException si la cuenta con ese correo no es la solicitante
     */
    @Transactional(readOnly = true)
    public DocumentFile requireOwnDocument(UUID applicationId, UUID documentId, String email) {
        StoreApplicationDocument document = findDocument(applicationId, documentId);
        if (!requireApplicant(document.getApplication()).email().equalsIgnoreCase(email)) {
            throw new AccessDeniedException("No tienes acceso a este documento.");
        }
        return toFile(document);
    }

    private StoreApplicationDocument findDocument(UUID applicationId, UUID documentId) {
        StoreApplicationDocument document = documentRepository.findWithApplicationById(documentId)
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_DOCUMENT_NOT_FOUND",
                        "No se encontró el documento."
                ));
        StoreApplication application = document.getApplication();
        if (!application.getId().equals(applicationId)) {
            throw new NotFoundException("SELLER_DOCUMENT_NOT_FOUND", "No se encontró el documento.");
        }
        return document;
    }

    private DocumentFile toFile(StoreApplicationDocument document) {
        return new DocumentFile(document.getObjectPath(), document.getOriginalFilename(), document.getContentType());
    }

    private AccountRef requireApplicant(StoreApplication application) {
        return accountLookup.findById(application.getApplicantAccountId())
                .orElseThrow(() -> new NotFoundException(
                        "ACCOUNT_NOT_FOUND",
                        "No se encontró la cuenta del solicitante."
                ));
    }

    private State activeStoreState() {
        return stateRepository.findByCode(StateCodes.ACTIVE)
                .orElseThrow(() -> new NotFoundException(
                        "STORE_ACTIVE_STATE_NOT_FOUND",
                        "No está configurado el estado activo para los almacenes."
                ));
    }

    private StoreApplicationView toView(StoreApplication application, AccountRef applicant) {
        List<StoreApplicationView.Document> documents =
                documentRepository.findAllOfApplication(application.getId())
                        .stream()
                        .map(document -> new StoreApplicationView.Document(
                                document.getId(),
                                document.getDocumentType(),
                                document.getOriginalFilename(),
                                document.getContentType(),
                                document.getSizeBytes()
                        ))
                        .toList();
        Store store = application.getStore();
        return new StoreApplicationView(
                application.getId(),
                store.getId(),
                store.getBusinessName(),
                store.getNit(),
                store.getBusinessEmail(),
                applicant.email(),
                application.getLegalType(),
                application.getStatus(),
                application.getPlanId(),
                application.getSelectedPlanId(),
                documents,
                application.getReviewNotes(),
                application.getCreatedAt()
        );
    }

    private String cleanNotes(String notes) {
        if (notes == null) {
            return null;
        }
        String cleaned = notes.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

}
