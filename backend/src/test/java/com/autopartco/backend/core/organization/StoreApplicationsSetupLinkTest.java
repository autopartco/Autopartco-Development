package com.autopartco.backend.core.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.organization.internal.Store;
import com.autopartco.backend.core.organization.internal.StoreApplication;
import com.autopartco.backend.core.organization.internal.StoreApplicationDocumentRepository;
import com.autopartco.backend.core.organization.internal.StoreApplicationRepository;
import com.autopartco.backend.core.organization.internal.StoreFileStorage;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.error.TooManyRequestsException;
import com.autopartco.backend.shared.persistence.StateCodes;
import com.autopartco.backend.shared.persistence.StateRepository;

@ExtendWith(MockitoExtension.class)
class StoreApplicationsSetupLinkTest {
    private static final String OWNER_EMAIL = "ana@example.com";
    private static final String ROOT_EMAIL = "root@example.com";

    @Mock
    private StoreApplicationRepository applicationRepository;
    @Mock
    private StoreApplicationDocumentRepository documentRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private StoreService storeService;
    @Mock
    private StoreFileStorage fileService;
    @Mock
    private AccountLookup accountLookup;
    @Mock
    private StateRepository stateRepository;

    @InjectMocks
    private StoreApplications storeApplications;

    private final UUID applicantId = UUID.randomUUID();
    private final Store store = Store.builder().id(UUID.randomUUID()).businessName("Repuestos Ana").build();
    private StoreApplication application;

    @BeforeEach
    void setUp() {
        application = StoreApplication.builder()
                .id(UUID.randomUUID())
                .applicantAccountId(applicantId)
                .store(store)
                .status(StoreApplicationStatus.CONFIGURATION_PENDING)
                .build();
        lenient().when(applicationRepository.lockById(application.getId())).thenReturn(Optional.of(application));
        lenient().when(accountLookup.findById(applicantId)).thenReturn(Optional.of(account(StateCodes.ACTIVE)));
        lenient().when(accountLookup.findByEmail(ROOT_EMAIL)).thenReturn(Optional.of(
                new AccountRef(UUID.randomUUID(), UUID.randomUUID(), ROOT_EMAIL, "Root", StateCodes.ACTIVE, "ROOT", "Root")));
    }

    @Test
    void ownerSetupLocksTheApplicationOfTheSessionAccount() {
        StoreApplications.PendingSetup setup = storeApplications.lockForOwnerSetup(application.getId(), "ANA@example.com");

        assertEquals(application.getId(), setup.applicationId());
        assertEquals(store.getId(), setup.storeId());
        assertEquals(applicantId, setup.applicantAccountId());
    }

    @Test
    void ownerSetupOfAnotherAccountIsNotFound() {
        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> storeApplications.lockForOwnerSetup(application.getId(), "otra@example.com")
        );

        assertEquals("SELLER_APPLICATION_NOT_FOUND", exception.getCode());
    }

    @Test
    void ownerSetupOfAnUnknownApplicationIsNotFound() {
        UUID unknown = UUID.randomUUID();
        lenient().when(applicationRepository.lockById(unknown)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> storeApplications.lockForOwnerSetup(unknown, OWNER_EMAIL));
    }

    @Test
    void ownerSetupOutsideConfigurationPendingIsAConflict() {
        application.setStatus(StoreApplicationStatus.ACTIVE);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> storeApplications.lockForOwnerSetup(application.getId(), OWNER_EMAIL)
        );

        assertEquals("SELLER_SETUP_NOT_PENDING", exception.getCode());
    }

    @Test
    void ownerSetupRequiresAVerifiedEmail() {
        lenient().when(accountLookup.findById(applicantId))
                .thenReturn(Optional.of(account("PENDING_VERIFICATION")));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> storeApplications.lockForOwnerSetup(application.getId(), OWNER_EMAIL)
        );

        assertEquals("SELLER_EMAIL_NOT_VERIFIED", exception.getCode());
    }

    @Test
    void setupLinkRequestForTheOwnerReturnsTheRecipientAndTouchesNoToken() {
        StoreApplications.SetupLink link =
                storeApplications.requestSetupLinkForOwner(application.getId(), OWNER_EMAIL);

        assertEquals(OWNER_EMAIL, link.applicantEmail());
        assertEquals("Ana", link.applicantGivenName());
        assertNull(application.getOnboardingTokenHash());
        assertNull(application.getOnboardingTokenExpiresAt());
    }

    @Test
    void aSecondSetupLinkRequestInsideTheCooldownIsRejectedWithTheCooldownCode() {
        storeApplications.requestSetupLinkForOwner(application.getId(), OWNER_EMAIL);

        TooManyRequestsException exception = assertThrows(
                TooManyRequestsException.class,
                () -> storeApplications.requestSetupLinkForOwner(application.getId(), OWNER_EMAIL)
        );

        assertEquals("SELLER_SETUP_LINK_COOLDOWN", exception.getCode());
    }

    @Test
    void setupLinkRequestOfAnotherAccountOrNonPendingApplicationFailsWithoutStartingTheCooldown() {
        assertThrows(
                NotFoundException.class,
                () -> storeApplications.requestSetupLinkForOwner(application.getId(), "otra@example.com")
        );
        application.setStatus(StoreApplicationStatus.PENDING_REVIEW);
        assertThrows(
                ConflictException.class,
                () -> storeApplications.requestSetupLinkForOwner(application.getId(), OWNER_EMAIL)
        );
        application.setStatus(StoreApplicationStatus.CONFIGURATION_PENDING);
        assertEquals(OWNER_EMAIL, storeApplications.requestSetupLinkForOwner(application.getId(), OWNER_EMAIL)
                .applicantEmail());
    }

    @Test
    void approvingMovesToConfigurationPendingWithoutGeneratingAToken() {
        application.setStatus(StoreApplicationStatus.PENDING_REVIEW);

        StoreApplications.Reviewed reviewed = storeApplications.review(
                application.getId(), ROOT_EMAIL, new StoreApplicationReviewRequest(true, null));

        assertTrue(reviewed.approved());
        assertEquals(StoreApplicationStatus.CONFIGURATION_PENDING, application.getStatus());
        assertNull(application.getOnboardingTokenHash());
        assertNull(application.getOnboardingTokenExpiresAt());
    }

    @Test
    void rejectingIsNotApproved() {
        application.setStatus(StoreApplicationStatus.PENDING_REVIEW);

        StoreApplications.Reviewed reviewed = storeApplications.review(
                application.getId(), ROOT_EMAIL, new StoreApplicationReviewRequest(false, "no"));

        assertFalse(reviewed.approved());
        assertEquals(StoreApplicationStatus.REJECTED, application.getStatus());
    }

    private AccountRef account(String stateCode) {
        return new AccountRef(applicantId, UUID.randomUUID(), OWNER_EMAIL, "Ana Pérez", stateCode, "CUSTOMER", "Ana");
    }
}
