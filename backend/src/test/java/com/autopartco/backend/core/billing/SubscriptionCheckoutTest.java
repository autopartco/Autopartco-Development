package com.autopartco.backend.core.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.autopartco.backend.shared.security.TokenHashing;
import com.autopartco.backend.core.billing.internal.wompi.WompiProperties;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.organization.StoreApplicationStatus;

@ExtendWith(MockitoExtension.class)
class SubscriptionCheckoutTest {
    private static final String APPLICANT_EMAIL = "owner@example.com";

    @Mock
    private SubscriptionPaymentRepository paymentRepository;

    @Mock
    private StoreSubscriptionRepository subscriptionRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private AccountLookup accountLookup;

    private final UUID storeId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();
    private final UUID applicantId = UUID.randomUUID();

    private SubscriptionCheckout checkout() {
        return new SubscriptionCheckout(
                paymentRepository,
                subscriptionRepository,
                planRepository,
                new WompiProperties("pub_test", "integrity", "events", "https://checkout.example/p/", "https://app.example/ok"),
                accountLookup
        );
    }

    private StoreSubscription activeSubscription() {
        return StoreSubscription.builder()
                .id(applicationId)
                .storeId(storeId)
                .applicantAccountId(applicantId)
                .status(StoreApplicationStatus.ACTIVE)
                .build();
    }

    private AccountRef applicant(String email) {
        return new AccountRef(applicantId, UUID.randomUUID(), email, "Owner", "ACTIVE", "STORE_MANAGER", "Owner");
    }

    @Test
    void rejectsAccountThatIsNotTheApplicant() {
        when(subscriptionRepository.lockByStoreId(storeId)).thenReturn(Optional.of(activeSubscription()));
        when(accountLookup.findById(applicantId)).thenReturn(Optional.of(applicant(APPLICANT_EMAIL)));

        assertThrows(
                AccessDeniedException.class,
                () -> checkout().createCheckout(storeId, "other@example.com", new CheckoutRequest(UUID.randomUUID(), 1))
        );
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(planRepository);
    }

    @Test
    void rejectsWhenApplicantAccountCannotBeResolvedBeforeRevealingPlanOrState() {
        // El solicitante ya no existe: nadie puede pagar por el almacén, ni siquiera con un correo coincidente.
        when(subscriptionRepository.lockByStoreId(storeId)).thenReturn(Optional.of(activeSubscription()));
        when(accountLookup.findById(applicantId)).thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> checkout().createCheckout(storeId, APPLICANT_EMAIL, new CheckoutRequest(UUID.randomUUID(), 1))
        );
        verifyNoInteractions(planRepository, paymentRepository);
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
        when(accountLookup.findById(applicantId)).thenReturn(Optional.of(applicant("OWNER@example.com")));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(paymentRepository.save(any(SubscriptionPayment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheckoutResponse response = checkout().createCheckout(storeId, APPLICANT_EMAIL, new CheckoutRequest(planId, 3));

        ArgumentCaptor<SubscriptionPayment> saved = ArgumentCaptor.forClass(SubscriptionPayment.class);
        verify(paymentRepository).save(saved.capture());
        SubscriptionPayment payment = saved.getValue();
        assertEquals(applicationId, payment.getStoreApplicationId());
        assertEquals(15_000_000L, payment.getAmountInCents());
        assertEquals(3, payment.getPrepaidMonths());
        assertEquals("PENDING", payment.getStatus());
        assertEquals(payment.getReference(), response.reference());
        assertEquals(15_000_000L, response.amountInCents());
        assertEquals("COP", response.currency());
        String signature = TokenHashing.sha256Hex(payment.getReference() + 15_000_000L + "COP" + "integrity");
        assertTrue(response.checkoutUrl().contains("signature:integrity=" + signature));
    }
}
