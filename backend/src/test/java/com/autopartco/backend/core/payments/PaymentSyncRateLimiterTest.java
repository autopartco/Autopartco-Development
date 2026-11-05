package com.autopartco.backend.core.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.shared.error.TooManyRequestsException;

class PaymentSyncRateLimiterTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-04T15:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void limitsPerKeyWithinTheWindowAndRecoversAfterIt() {
        MutableClock clock = new MutableClock();
        PaymentSyncRateLimiter limiter = new PaymentSyncRateLimiter(clock);
        for (int i = 0; i < PaymentSyncRateLimiter.MAX_REQUESTS; i++) {
            limiter.acquire("owner@example.com");
        }

        TooManyRequestsException error = assertThrows(TooManyRequestsException.class,
                () -> limiter.acquire("owner@example.com"));
        assertThat(error.getCode()).isEqualTo("PAYMENT_SYNC_RATE_LIMITED");
        assertDoesNotThrow(() -> limiter.acquire("other@example.com"));

        clock.now = clock.now.plus(PaymentSyncRateLimiter.WINDOW);
        assertDoesNotThrow(() -> limiter.acquire("owner@example.com"));
    }
}
