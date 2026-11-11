package com.autopartco.backend.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.shared.error.TooManyRequestsException;

class SlidingWindowRateLimiterTest {
    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-08T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final SlidingWindowRateLimiter limiter =
            new SlidingWindowRateLimiter("TEST_LIMITED", 3, Duration.ofMinutes(1), 100, clock);

    @Test
    void allowsMaxThenThrowsWithCode() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire("1.1.1.1");
        }
        TooManyRequestsException ex = assertThrows(TooManyRequestsException.class, () -> limiter.acquire("1.1.1.1"));
        assertThat(ex.getCode()).isEqualTo("TEST_LIMITED");
        assertDoesNotThrow(() -> limiter.acquire("2.2.2.2"));
    }

    @Test
    void releasesAfterWindow() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire("k");
        }
        clock.now = clock.now.plus(Duration.ofMinutes(1));
        assertDoesNotThrow(() -> limiter.acquire("k"));
    }

    @Test
    void hardCapRejectsNewKeysWhileActiveAndAdmitsAfterWindow() {
        SlidingWindowRateLimiter capped =
                new SlidingWindowRateLimiter("CAP_LIMITED", 3, Duration.ofMinutes(1), 2, clock);
        capped.acquire("a");
        capped.acquire("b");
        TooManyRequestsException ex = assertThrows(TooManyRequestsException.class, () -> capped.acquire("c"));
        assertThat(ex.getCode()).isEqualTo("CAP_LIMITED");
        assertDoesNotThrow(() -> capped.acquire("a"));
        clock.now = clock.now.plus(Duration.ofMinutes(1));
        assertDoesNotThrow(() -> capped.acquire("c"));
    }

    @Test
    void nullKeyIsTrackedTogether() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire(null);
        }
        assertThrows(TooManyRequestsException.class, () -> limiter.acquire(null));
    }
}
