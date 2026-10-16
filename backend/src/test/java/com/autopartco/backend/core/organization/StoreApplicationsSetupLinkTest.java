package com.autopartco.backend.core.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

import java.time.LocalDateTime;
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
import com.autopartco.backend.shared.security.TokenHashing;

@ExtendWith(MockitoExtension.class)
class StoreApplicationsSetupLinkTest {
    private static final String OWNER_EMAIL = "ana@example.com";

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
                .onboardingTokenHash(TokenHashing.sha256Hex("old-token"))
                // emitido hace 1 hora: fuera del enfriamiento de 5 minutos
                .onboardingTokenExpiresAt(LocalDateTime.now().plusHours(47))
                .build();
        lenient().when(applicationRepository.lockById(application.getId())).thenReturn(Optional.of(application));
        lenient().when(accountLookup.findById(applicantId)).thenReturn(Optional.of(account(StateCodes.ACTIVE)));
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
    void reissueReplacesTheTokenSoThePreviousOneStopsMatching() {
        String previousHash = application.getOnboardingTokenHash();

        StoreApplications.SetupLink link =
                storeApplications.reissueOnboardingTokenForOwner(application.getId(), OWNER_EMAIL);

        assertEquals(OWNER_EMAIL, link.applicantEmail());
        assertNotEquals(previousHash, application.getOnboardingTokenHash());
        assertEquals(TokenHashing.sha256Hex(link.onboardingToken()), application.getOnboardingTokenHash());
        assertNotEquals(TokenHashing.sha256Hex("old-token"), application.getOnboardingTokenHash());
        assertTrue(application.getOnboardingTokenExpiresAt().isAfter(LocalDateTime.now().plusHours(47)));
    }

    @Test
    void afterAReissueLockForSetupRejectsThePreviousTokenAndAcceptsTheNewOne() {
        // El repositorio simulado resuelve por hash contra el estado actual de la solicitud.
        lenient().when(applicationRepository.lockByOnboardingToken(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(call -> call.getArgument(0).equals(application.getOnboardingTokenHash())
                        ? Optional.of(application)
                        : Optional.empty());
        assertEquals(application.getId(), storeApplications.lockForSetup("old-token").applicationId());

        StoreApplications.SetupLink link =
                storeApplications.reissueOnboardingTokenForOwner(application.getId(), OWNER_EMAIL);

        assertEquals("SELLER_SETUP_TOKEN_INVALID", assertThrows(
                com.autopartco.backend.shared.error.BadRequestException.class,
                () -> storeApplications.lockForSetup("old-token")
        ).getCode());
        assertEquals(application.getId(), storeApplications.lockForSetup(link.onboardingToken()).applicationId());
    }

    @Test
    void reissueRightAfterTheLastEmailIsRejectedWithTheCooldownCode() {
        application.setOnboardingTokenExpiresAt(LocalDateTime.now().plusHours(48).minusMinutes(1));
        String hash = application.getOnboardingTokenHash();

        TooManyRequestsException exception = assertThrows(
                TooManyRequestsException.class,
                () -> storeApplications.reissueOnboardingTokenForOwner(application.getId(), OWNER_EMAIL)
        );

        assertEquals("SELLER_SETUP_LINK_COOLDOWN", exception.getCode());
        assertEquals(hash, application.getOnboardingTokenHash());
    }

    @Test
    void reissueOfAnotherAccountOrNonPendingApplicationFails() {
        assertThrows(
                NotFoundException.class,
                () -> storeApplications.reissueOnboardingTokenForOwner(application.getId(), "otra@example.com")
        );
        application.setStatus(StoreApplicationStatus.PENDING_REVIEW);
        assertThrows(
                ConflictException.class,
                () -> storeApplications.reissueOnboardingTokenForOwner(application.getId(), OWNER_EMAIL)
        );
    }

    @Test
    void reissueByExpiredTokenStillWorks() {
        application.setOnboardingTokenExpiresAt(LocalDateTime.now().minusHours(3));
        org.mockito.Mockito.when(applicationRepository.lockByOnboardingToken(TokenHashing.sha256Hex("old-token")))
                .thenReturn(Optional.of(application));

        Optional<StoreApplications.SetupLink> link = storeApplications.reissueOnboardingTokenByToken("old-token");

        assertTrue(link.isPresent());
        assertEquals(TokenHashing.sha256Hex(link.get().onboardingToken()), application.getOnboardingTokenHash());
    }

    @Test
    void reissueByTokenIsEmptyForUnknownTokensNonPendingApplicationsAndCooldown() {
        org.mockito.Mockito.when(applicationRepository.lockByOnboardingToken(TokenHashing.sha256Hex("unknown")))
                .thenReturn(Optional.empty());
        assertFalse(storeApplications.reissueOnboardingTokenByToken("unknown").isPresent());
        assertFalse(storeApplications.reissueOnboardingTokenByToken(" ").isPresent());
        assertFalse(storeApplications.reissueOnboardingTokenByToken(null).isPresent());

        org.mockito.Mockito.when(applicationRepository.lockByOnboardingToken(TokenHashing.sha256Hex("old-token")))
                .thenReturn(Optional.of(application));
        application.setStatus(StoreApplicationStatus.ACTIVE);
        assertFalse(storeApplications.reissueOnboardingTokenByToken("old-token").isPresent());

        application.setStatus(StoreApplicationStatus.CONFIGURATION_PENDING);
        application.setOnboardingTokenExpiresAt(LocalDateTime.now().plusHours(48).minusMinutes(1));
        assertFalse(storeApplications.reissueOnboardingTokenByToken("old-token").isPresent());
    }

    private AccountRef account(String stateCode) {
        return new AccountRef(applicantId, UUID.randomUUID(), OWNER_EMAIL, "Ana Pérez", stateCode, "CUSTOMER", "Ana");
    }
}
