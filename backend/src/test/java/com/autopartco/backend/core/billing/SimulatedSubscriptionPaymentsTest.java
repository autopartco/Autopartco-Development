package com.autopartco.backend.core.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.billing.SimulatedPaymentRequest.Outcome;
import com.autopartco.backend.core.billing.internal.Plan;
import com.autopartco.backend.core.billing.internal.PlanRepository;
import com.autopartco.backend.core.billing.internal.StoreSubscription;
import com.autopartco.backend.core.billing.internal.StoreSubscriptionRepository;
import com.autopartco.backend.core.billing.internal.SubscriptionEvents;
import com.autopartco.backend.core.billing.internal.SubscriptionPayment;
import com.autopartco.backend.core.billing.internal.SubscriptionPaymentRepository;
import com.autopartco.backend.core.billing.internal.wompi.WompiProperties;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StoreApplicationStatus;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.PaymentRequiredException;

@ExtendWith(MockitoExtension.class)
class SimulatedSubscriptionPaymentsTest {
    private static final String EMAIL = "owner@example.com";

    @Mock
    private SubscriptionPaymentRepository paymentRepository;

    @Mock
    private StoreSubscriptionRepository subscriptionRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private SubscriptionEvents subscriptionEvents;

    @Mock
    private StoreAccess storeAccess;

    @Mock
    private AccountLookup accountLookup;

    private final UUID storeId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private final UUID clientRequestId = UUID.randomUUID();
    private Plan business;
    private StoreSubscription subscription;

    private SimulatedSubscriptionPayments payments(boolean enabled) {
        return new SimulatedSubscriptionPayments(
                paymentRepository,
                subscriptionRepository,
                planRepository,
                subscriptionEvents,
                storeAccess,
                accountLookup,
                new BillingSettings(enabled, new WompiProperties(null, null, null, null, null))
        );
    }

    @BeforeEach
    void setUp() {
        business = Plan.builder()
                .id(planId)
                .code("BUSINESS")
                .name("Negocio")
                .monthlyPriceCop(149_900)
                .currency("COP")
                .features(List.of())
                .maxBranches(5)
                .capabilities(List.of("CSV_IMPORT"))
                .enabled(true)
                .build();
        subscription = StoreSubscription.builder()
                .id(applicationId)
                .storeId(storeId)
                .status(StoreApplicationStatus.ACTIVE)
                .trialEndsAt(LocalDateTime.now().plusDays(10))
                .build();
    }

    private SimulatedPaymentRequest request(Outcome outcome) {
        return new SimulatedPaymentRequest(planId, 3, outcome, clientRequestId);
    }

    private void stubHappyPath() {
        when(subscriptionRepository.lockByStoreId(storeId)).thenReturn(Optional.of(subscription));
        when(paymentRepository.findByReference("SIM-" + clientRequestId)).thenReturn(Optional.empty());
        when(planRepository.findById(planId)).thenReturn(Optional.of(business));
    }

    @Test
    void approvedPaymentExtendsPeriodAndStoresProviderSnapshotAndEvent() {
        stubHappyPath();
        when(accountLookup.findByEmail(EMAIL)).thenReturn(Optional.of(
                new AccountRef(accountId, UUID.randomUUID(), EMAIL, "Ana", "ACTIVE", "SELLER", "Ana")));
        when(paymentRepository.saveAndFlush(any(SubscriptionPayment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDateTime trialEnd = subscription.getTrialEndsAt();

        SimulatedPaymentResult result = payments(true).pay(storeId, EMAIL, request(Outcome.APPROVED));

        assertTrue(result.created());
        ArgumentCaptor<SubscriptionPayment> saved = ArgumentCaptor.forClass(SubscriptionPayment.class);
        verify(paymentRepository).saveAndFlush(saved.capture());
        SubscriptionPayment payment = saved.getValue();
        assertEquals("SIMULATED", payment.getProvider());
        assertEquals("SIM-" + clientRequestId, payment.getReference());
        assertEquals("APPROVED", payment.getStatus());
        assertEquals(149_900L * 3 * 100, payment.getAmountInCents());
        assertEquals(trialEnd, payment.getPeriodStartsAt());
        assertEquals(trialEnd.plusMonths(3), payment.getPeriodEndsAt());
        assertNotNull(payment.getPaidAt());
        assertEquals(accountId, payment.getRequestedByAccountId());
        assertEquals("BUSINESS", payment.getPlanSnapshot().get("code"));
        assertEquals(149_900L, payment.getPlanSnapshot().get("monthlyPriceCop"));
        assertEquals(trialEnd.plusMonths(3), subscription.getPaidThrough());
        assertEquals(business, subscription.getPlan());
        verify(subscriptionEvents).paymentApproved(eq(subscription), eq(payment), eq(null), eq(accountId), any());
        assertEquals(449_700L, result.payment().amountCop());
    }

    @Test
    void uniqueReferenceViolationIsTranslatedToGenericDuplicateRequest() {
        stubHappyPath();
        when(accountLookup.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any(SubscriptionPayment.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk reference"));

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> payments(true).pay(storeId, EMAIL, request(Outcome.APPROVED))
        );

        assertEquals("SUBSCRIPTION_DUPLICATE_REQUEST", error.getCode());
        assertFalse(error.getMessage().toLowerCase().contains("almacén"));
        verifyNoInteractions(subscriptionEvents);
    }

    @Test
    void rejectedPaymentReturns402WithoutPersisting() {
        stubHappyPath();

        PaymentRequiredException error = assertThrows(
                PaymentRequiredException.class,
                () -> payments(true).pay(storeId, EMAIL, request(Outcome.REJECTED))
        );

        assertEquals("PAYMENT_REJECTED", error.getCode());
        assertEquals(Map.of("amountCop", 449_700L, "currency", "COP"), error.getDetails());
        verify(paymentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(subscriptionEvents);
        assertEquals(null, subscription.getPaidThrough());
    }

    @Test
    void repeatedRequestReturnsTheSamePayment() {
        when(subscriptionRepository.lockByStoreId(storeId)).thenReturn(Optional.of(subscription));
        SubscriptionPayment original = SubscriptionPayment.builder()
                .id(UUID.randomUUID())
                .storeApplicationId(applicationId)
                .plan(business)
                .reference("SIM-" + clientRequestId)
                .amountInCents(44_970_000L)
                .prepaidMonths(3)
                .currency("COP")
                .status("APPROVED")
                .provider("SIMULATED")
                .build();
        when(paymentRepository.findByReference("SIM-" + clientRequestId)).thenReturn(Optional.of(original));

        SimulatedPaymentResult result = payments(true).pay(storeId, EMAIL, request(Outcome.APPROVED));

        assertFalse(result.created());
        assertEquals(original.getId(), result.payment().id());
        verify(paymentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(subscriptionEvents);
    }

    @Test
    void keyUsedByAnotherStoreIsConflict() {
        when(subscriptionRepository.lockByStoreId(storeId)).thenReturn(Optional.of(subscription));
        SubscriptionPayment foreign = SubscriptionPayment.builder()
                .storeApplicationId(UUID.randomUUID())
                .plan(business)
                .reference("SIM-" + clientRequestId)
                .build();
        when(paymentRepository.findByReference("SIM-" + clientRequestId)).thenReturn(Optional.of(foreign));

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> payments(true).pay(storeId, EMAIL, request(Outcome.APPROVED))
        );

        assertEquals("SUBSCRIPTION_DUPLICATE_REQUEST", error.getCode());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void switchedOffSimulatorIsBadRequest() {
        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> payments(false).pay(storeId, EMAIL, request(Outcome.APPROVED))
        );

        assertEquals("PAYMENT_METHOD_NOT_SUPPORTED", error.getCode());
        verifyNoInteractions(paymentRepository, subscriptionRepository);
    }

    @Test
    void nonOwnerIsDenied() {
        doThrow(new AccessDeniedException("owner required")).when(storeAccess).requireOwner(EMAIL, storeId);

        assertThrows(
                AccessDeniedException.class,
                () -> payments(true).pay(storeId, EMAIL, request(Outcome.APPROVED))
        );
        verifyNoInteractions(paymentRepository, subscriptionRepository, planRepository);
    }

    @Test
    void differentPlanWhilePaidPeriodIsActiveIsLocked() {
        Plan current = Plan.builder().id(UUID.randomUUID()).code("BASIC").name("Básico").monthlyPriceCop(59_900)
                .currency("COP").features(List.of()).capabilities(List.of()).enabled(true).build();
        subscription.setPlan(current);
        subscription.setPaidThrough(LocalDateTime.now().plusMonths(1));
        stubHappyPath();

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> payments(true).pay(storeId, EMAIL, request(Outcome.APPROVED))
        );

        assertEquals("SUBSCRIPTION_PLAN_CHANGE_LOCKED", error.getCode());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void freePlanIsNotPayable() {
        Plan free = Plan.builder().id(planId).code("TRY").name("Prueba").monthlyPriceCop(0).currency("COP")
                .features(List.of()).capabilities(List.of()).enabled(true).build();
        when(subscriptionRepository.lockByStoreId(storeId)).thenReturn(Optional.of(subscription));
        when(paymentRepository.findByReference("SIM-" + clientRequestId)).thenReturn(Optional.empty());
        when(planRepository.findById(planId)).thenReturn(Optional.of(free));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> payments(true).pay(storeId, EMAIL, request(Outcome.APPROVED))
        );

        assertEquals("SELLER_PLAN_NOT_PAYABLE", error.getCode());
    }
}
