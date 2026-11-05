package com.autopartco.backend.core.payments;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.autopartco.backend.shared.error.TooManyRequestsException;

/**
 * Límite de {@code POST …/payment/sync} por usuario: {@value #MAX_REQUESTS} llamadas por ventana de un minuto. En
 * memoria y por instancia (ADR 0004 §4): no hay limitador compartido.
 */
@Component
public class PaymentSyncRateLimiter {
    static final int MAX_REQUESTS = 10;
    static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Deque<Instant>> calls = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public PaymentSyncRateLimiter() {
        this(Clock.systemUTC());
    }

    PaymentSyncRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Registra una llamada de {@code key} (p. ej. el correo de la sesión).
     *
     * @throws TooManyRequestsException {@code PAYMENT_SYNC_RATE_LIMITED} si supera el límite
     */
    public void acquire(String key) {
        Instant now = clock.instant();
        if (calls.size() > MAX_TRACKED_KEYS) {
            calls.entrySet().removeIf(entry -> isIdle(entry.getValue(), now));
        }
        Deque<Instant> window = calls.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && !window.peekFirst().isAfter(now.minus(WINDOW))) {
                window.pollFirst();
            }
            if (window.size() >= MAX_REQUESTS) {
                throw new TooManyRequestsException(
                        "PAYMENT_SYNC_RATE_LIMITED",
                        "Demasiadas verificaciones del pago. Espera un momento e inténtalo de nuevo."
                );
            }
            window.addLast(now);
        }
    }

    private static boolean isIdle(Deque<Instant> window, Instant now) {
        synchronized (window) {
            return window.isEmpty() || !window.peekLast().isAfter(now.minus(WINDOW));
        }
    }
}
