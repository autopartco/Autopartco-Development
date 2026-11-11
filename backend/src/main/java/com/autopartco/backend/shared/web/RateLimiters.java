package com.autopartco.backend.shared.web;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Crea {@link SlidingWindowRateLimiter} sobre Redis cuando hay {@link RateLimitStore} (con {@code REDIS_URL}); si no,
 * en memoria. Cada limitador usa su propio {@code name} como prefijo en Redis.
 */
@Component
public class RateLimiters {
    public static final int DEFAULT_MAX_TRACKED = 20_000;

    private final RateLimitStore store;
    private final Clock clock;

    @Autowired
    public RateLimiters(ObjectProvider<RateLimitStore> store) {
        this(store.getIfAvailable(), Clock.systemUTC());
    }

    public RateLimiters(RateLimitStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /** Solo memoria (pruebas). */
    public static RateLimiters inMemory(Clock clock) {
        return new RateLimiters((RateLimitStore) null, clock);
    }

    public SlidingWindowRateLimiter create(String name, String errorCode, int maxPerWindow, Duration window) {
        return new SlidingWindowRateLimiter(name, errorCode, maxPerWindow, window, DEFAULT_MAX_TRACKED, clock, store);
    }
}
