package com.autopartco.backend.storesuite.onboarding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.billing.PlanResponse;
import com.autopartco.backend.core.billing.PlanService;
import com.autopartco.backend.core.billing.Subscriptions;
import com.autopartco.backend.core.identity.AccountProvisioning;
import com.autopartco.backend.core.identity.LoginResponse;
import com.autopartco.backend.core.identity.SessionIssuer;
import com.autopartco.backend.core.organization.StoreApplicationStatus;
import com.autopartco.backend.core.organization.StoreApplications;
import com.autopartco.backend.core.organization.StoreFiles;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

@ExtendWith(MockitoExtension.class)
class CompleteStoreSetupAuthenticatedTest {
    @Mock
    private StoreApplications storeApplications;
    @Mock
    private StoreFiles storeFiles;
    @Mock
    private Subscriptions subscriptions;
    @Mock
    private PlanService planService;
    @Mock
    private AccountProvisioning accountProvisioning;
    @Mock
    private SessionIssuer sessionIssuer;

    @InjectMocks
    private CompleteStoreSetup completeStoreSetup;

    private final UUID applicationId = UUID.randomUUID();
    private final UUID storeId = UUID.randomUUID();
    private final UUID applicantId = UUID.randomUUID();

    @Test
    void sessionSetupActivatesTheStorePromotesTheAccountAndReturnsNewTokens() {
        LoginResponse tokens = new LoginResponse("access", "refresh", "Bearer", 900);
        LocalDateTime trialEnd = LocalDateTime.now().plusDays(30);
        PlanResponse plan = org.mockito.Mockito.mock(PlanResponse.class);
        when(storeApplications.lockForOwnerSetup(applicationId, "ana@example.com"))
                .thenReturn(new StoreApplications.PendingSetup(applicationId, storeId, applicantId));
        when(subscriptions.startTrial(any(), any())).thenReturn(trialEnd);
        when(storeApplications.completeConfiguration(any(), any())).thenReturn(StoreApplicationStatus.ACTIVE);
        when(planService.recommended()).thenReturn(plan);
        when(sessionIssuer.issue(applicantId, "127.0.0.1", "agent")).thenReturn(tokens);

        StoreSetupResponse response = completeStoreSetup.executeAuthenticated(
                applicationId, "ana@example.com", "Repuestos", null, null, "127.0.0.1", "agent");

        verify(storeApplications).configureStore(storeId, null, null, "Repuestos");
        verify(accountProvisioning).promoteToStoreManager(applicantId);
        assertEquals(storeId, response.storeId());
        assertEquals("ACTIVE", response.status());
        assertEquals(trialEnd, response.trialEndsAt());
        assertSame(plan, response.recommendedPlan());
        assertSame(tokens, response.authentication());
    }

    @Test
    void sessionSetupOfAnotherAccountChangesNothing() {
        when(storeApplications.lockForOwnerSetup(applicationId, "otra@example.com"))
                .thenThrow(new NotFoundException("SELLER_APPLICATION_NOT_FOUND", "No existe"));

        assertThrows(NotFoundException.class, () -> completeStoreSetup.executeAuthenticated(
                applicationId, "otra@example.com", null, null, null, "ip", "ua"));

        verify(storeApplications, never()).configureStore(any(), any(), any(), any());
        verifyNoInteractions(accountProvisioning, subscriptions, sessionIssuer);
    }

    @Test
    void sessionSetupOfANonPendingApplicationIsAConflict() {
        when(storeApplications.lockForOwnerSetup(applicationId, "ana@example.com"))
                .thenThrow(new ConflictException("SELLER_SETUP_NOT_PENDING", "No pendiente"));

        assertThrows(ConflictException.class, () -> completeStoreSetup.executeAuthenticated(
                applicationId, "ana@example.com", null, null, null, "ip", "ua"));

        verifyNoInteractions(accountProvisioning, subscriptions, sessionIssuer);
    }
}
