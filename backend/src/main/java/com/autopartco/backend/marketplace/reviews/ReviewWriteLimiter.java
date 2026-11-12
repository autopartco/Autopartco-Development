package com.autopartco.backend.marketplace.reviews;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.autopartco.backend.shared.web.RateLimiters;
import com.autopartco.backend.shared.web.SlidingWindowRateLimiter;

/**
 * Límite de escrituras de reseñas (crear, editar, borrar; almacén y producto juntos): {@value #MAX_PER_HOUR} por hora
 * y por cuenta. Exceso: 429 {@value #ERROR_CODE}. En Redis si está configurado ({@link RateLimiters}).
 */
@Component
public class ReviewWriteLimiter {
    static final int MAX_PER_HOUR = 10;
    static final String ERROR_CODE = "REVIEW_RATE_LIMITED";

    private final SlidingWindowRateLimiter limiter;

    @Autowired
    public ReviewWriteLimiter(RateLimiters rateLimiters) {
        this.limiter = rateLimiters.create("review-write", ERROR_CODE, MAX_PER_HOUR, Duration.ofHours(1));
    }

    /** Solo memoria (pruebas). */
    static ReviewWriteLimiter inMemory(Clock clock) {
        return new ReviewWriteLimiter(RateLimiters.inMemory(clock));
    }

    void acquire(UUID accountId) {
        limiter.acquire(accountId == null ? null : accountId.toString());
    }
}
