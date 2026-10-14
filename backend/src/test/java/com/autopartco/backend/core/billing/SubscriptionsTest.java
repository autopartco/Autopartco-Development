package com.autopartco.backend.core.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.billing.internal.Plan;
import com.autopartco.backend.core.billing.internal.StoreSubscription;
import com.autopartco.backend.core.billing.internal.StoreSubscriptionRepository;
import com.autopartco.backend.core.billing.internal.SubscriptionEventRepository;
import com.autopartco.backend.core.organization.StoreApplicationStatus;

@ExtendWith(MockitoExtension.class)
class SubscriptionsTest {
    private static final LocalDateTime SETUP_AT = LocalDateTime.of(2026, 10, 14, 10, 0);

    @Mock
    private StoreSubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionEventRepository eventRepository;

    @Mock
    private PlanService planService;

    @InjectMocks
    private Subscriptions subscriptions;

    @Test
    void startTrialSetsOneMonthWindowAndRecordsEventOnce() {
        UUID storeId = UUID.randomUUID();
        Plan trial = mock(Plan.class);
        StoreSubscription subscription = pending(storeId);
        when(subscriptionRepository.findByStoreId(storeId)).thenReturn(Optional.of(subscription));
        when(planService.trialPlan()).thenReturn(trial);

        LocalDateTime end = subscriptions.startTrial(storeId, SETUP_AT);

        assertEquals(SETUP_AT.plusMonths(1), end);
        assertEquals(SETUP_AT, subscription.getTrialStartsAt());
        verify(eventRepository).save(any());
    }

    @Test
    void startTrialIsIdempotentAndKeepsExistingDates() {
        UUID storeId = UUID.randomUUID();
        StoreSubscription subscription = pending(storeId);
        subscription.setTrialStartsAt(SETUP_AT);
        subscription.setTrialEndsAt(SETUP_AT.plusMonths(1));
        when(subscriptionRepository.findByStoreId(storeId)).thenReturn(Optional.of(subscription));

        LocalDateTime end = subscriptions.startTrial(storeId, SETUP_AT.plusDays(5));

        assertEquals(SETUP_AT.plusMonths(1), end);
        assertEquals(SETUP_AT, subscription.getTrialStartsAt());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void viewReportsTrialingWithDaysRemaining() {
        UUID storeId = UUID.randomUUID();
        StoreSubscription subscription = pending(storeId);
        subscription.setTrialStartsAt(SETUP_AT);
        subscription.setTrialEndsAt(SETUP_AT.plusMonths(1));
        when(subscriptionRepository.findByStoreId(storeId)).thenReturn(Optional.of(subscription));
        when(planService.trialPlan()).thenReturn(mock(Plan.class));

        SubscriptionView view = subscriptions.view(storeId, SETUP_AT.plusDays(10));

        assertEquals(SubscriptionStatus.TRIALING, view.status());
        assertEquals(SETUP_AT.plusMonths(1), view.validThrough());
        assertEquals(21L, view.daysRemaining());
        assertNull(view.paidThrough());
    }

    private static StoreSubscription pending(UUID storeId) {
        return StoreSubscription.builder()
                .id(UUID.randomUUID())
                .storeId(storeId)
                .status(StoreApplicationStatus.ACTIVE)
                .build();
    }
}
