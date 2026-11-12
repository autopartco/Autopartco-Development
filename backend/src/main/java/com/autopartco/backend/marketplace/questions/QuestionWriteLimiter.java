package com.autopartco.backend.marketplace.questions;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.autopartco.backend.shared.web.RateLimiters;
import com.autopartco.backend.shared.web.SlidingWindowRateLimiter;

/**
 * Límite de preguntas nuevas: {@value #MAX_PER_HOUR} por hora y por cuenta. Exceso: 429 {@value #ERROR_CODE}. En Redis
 * si está configurado ({@link RateLimiters}).
 */
@Component
public class QuestionWriteLimiter {
    static final int MAX_PER_HOUR = 5;
    static final String ERROR_CODE = "QUESTION_RATE_LIMITED";

    private final SlidingWindowRateLimiter limiter;

    @Autowired
    public QuestionWriteLimiter(RateLimiters rateLimiters) {
        this.limiter = rateLimiters.create("question-write", ERROR_CODE, MAX_PER_HOUR, Duration.ofHours(1));
    }

    /** Solo memoria (pruebas). */
    static QuestionWriteLimiter inMemory(Clock clock) {
        return new QuestionWriteLimiter(RateLimiters.inMemory(clock));
    }

    void acquire(UUID accountId) {
        limiter.acquire(accountId == null ? null : accountId.toString());
    }
}
