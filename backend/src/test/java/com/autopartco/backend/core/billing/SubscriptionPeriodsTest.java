package com.autopartco.backend.core.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.billing.internal.SubscriptionPeriods;
import com.autopartco.backend.core.organization.StoreApplicationStatus;

class SubscriptionPeriodsTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 10, 0);

    @Test
    void notActiveApplicationIsPendingSetupEvenWithDates() {
        assertEquals(SubscriptionStatus.PENDING_SETUP, SubscriptionPeriods.statusAt(
                StoreApplicationStatus.CONFIGURATION_PENDING, NOW.plusDays(5), NOW.plusDays(5), NOW));
    }

    @Test
    void paidThroughInTheFutureIsActive() {
        assertEquals(SubscriptionStatus.ACTIVE, SubscriptionPeriods.statusAt(
                StoreApplicationStatus.ACTIVE, NOW.plusDays(1), NOW.minusDays(1), NOW));
    }

    @Test
    void trialInTheFutureIsTrialing() {
        assertEquals(SubscriptionStatus.TRIALING, SubscriptionPeriods.statusAt(
                StoreApplicationStatus.ACTIVE, null, NOW.plusDays(1), NOW));
    }

    @Test
    void pastPeriodsAreExpired() {
        assertEquals(SubscriptionStatus.EXPIRED, SubscriptionPeriods.statusAt(
                StoreApplicationStatus.ACTIVE, NOW, NOW, NOW));
    }

    @Test
    void trialEndAddsCalendarMonths() {
        assertEquals(LocalDateTime.of(2026, 11, 14, 10, 0), SubscriptionPeriods.trialEnd(NOW, 1));
    }
}
