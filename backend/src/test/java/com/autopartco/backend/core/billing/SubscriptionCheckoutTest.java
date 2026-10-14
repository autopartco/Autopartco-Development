package com.autopartco.backend.core.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.billing.internal.Plan;
import com.autopartco.backend.core.billing.internal.PlanRepository;
import com.autopartco.backend.core.billing.internal.StoreSubscription;
import com.autopartco.backend.core.billing.internal.StoreSubscriptionRepository;
import com.autopartco.backend.core.billing.internal.SubscriptionPayment;
import com.autopartco.backend.core.billing.internal.SubscriptionPaymentRepository;
import com.autopartco.backend.core.billing.internal.wompi.WompiProperties;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StoreApplicationStatus;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.security.TokenHashing;

@ExtendWith(MockitoExtension.class)
class SubscriptionCheckoutTest {
    private static final LocalDateTime NOW = LocalDateTime.now();

    @Mock
    private SubscriptionPaymentRepository paymentRepository;

    @Mock
    private StoreSubscriptionRepository subscriptionRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private StoreAccess storeAccess;

    private final UUID storeId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();

    private SubscriptionCheckout checkout() {
        return new SubscriptionCheckout(
                paymentRepository,
                subscriptionRepository,
                planRepository,
                new WompiProperties("pub_test", "integrity", "events", "https://checkout.example/p/", "https://app.example/ok"),
                storeAccess
        );
    }

    private StoreSubscription activeSubscription() {
        return StoreSubscription.builder()
                .id(applicationId)
                .storeId(storeId)
                .status(StoreApplicationStatus.ACTIVE)
                .build();
    }

    @Test
    void rejectsCallersWhoAreNotOwners() {
        doThrow(new AccessDeniedException("owner required"))
                .when(storeAccess).requireOwner("collab@example.com", storeId);

        assertThrows(
                AccessDeniedException.class,
                () -> checkout().createCheckout(storeId, "collab@example.com", new CheckoutRequest(UUID.randomUUID(), 1))
        );
        verifyNoInteractions(paymentRepository, planRepository);
    }

    @Test
    void rejectsPlanChangeWhilePaidPeriodIsActive() {
        Plan current = mock(Plan.class);
        UUID currentId = UUID.randomUUID();
        when(current.getId()).thenReturn(currentId);
        when(current.getCode()).thenReturn("BUSINESS");
        StoreSubscription subscription = activeSubscription();
        subscription.setPlan(current);
        subscription.setPaidThrough(NOW.plusMonths(1));
        UUID requestedPlanId = UUID.randomUUID();
        Plan requested = Plan.builder()
                .code("ENTERPRISE")
                .monthlyPriceCop(499_900)
                .currency("COP")
                .features(List.of())
                .capabilities(List.of())
                .enabled(true)
                .build();
        when(subscriptionRepository.lockByStoreId(storeId)).thenReturn(Optional.of(subscription));
        when(planRepository.findById(requestedPlanId)).thenReturn(Optional.of(requested));

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> checkout().createCheckout(storeId, "owner@example.com", new CheckoutRequest(requestedPlanId, 1))
        );

        assertEquals("SUBSCRIPTION_PLAN_CHANGE_LOCKED", error.getCode());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createsPendingPaymentForApplicationRowAndSignedCheckoutUrl() {
        UUID planId = UUID.randomUUID();
        Plan plan = Plan.builder()
                .code("BUSINESS")
                .monthlyPriceCop(50000)
                .currency("COP")
                .features(List.of())
                .capabilities(List.of())
                .enabled(true)
                .build();
        when(subscriptionRepository.lockByStoreId(storeId)).thenReturn(Optional.of(activeSubscription()));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(paymentRepository.save(any(SubscriptionPayment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheckoutResponse response = checkout().createCheckout(storeId, "owner@example.com", new CheckoutRequest(planId, 3));

        ArgumentCaptor<SubscriptionPayment> saved = ArgumentCaptor.forClass(SubscriptionPayment.class);
        verify(paymentRepository).save(saved.capture());
        SubscriptionPayment payment = saved.getValue();
        assertEquals(applicationId, payment.getStoreApplicationId());
        assertEquals(15_000_000L, payment.getAmountInCents());
        assertEquals(3, payment.getPrepaidMonths());
        assertEquals("PENDING", payment.getStatus());
        assertEquals("WOMPI", payment.getProvider());
        assertEquals(payment.getReference(), response.reference());
        assertEquals(15_000_000L, response.amountInCents());
        assertEquals("COP", response.currency());
        String signature = TokenHashing.sha256Hex(payment.getReference() + 15_000_000L + "COP" + "integrity");
        assertTrue(response.checkoutUrl().contains("signature:integrity=" + signature));
        verify(storeAccess).requireOwner(eq("owner@example.com"), eq(storeId));
    }
}
