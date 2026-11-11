package com.autopartco.backend.shared.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.autopartco.backend.shared.error.TooManyRequestsException;

/**
 * Límite de llamadas por clave (p. ej. IP) con ventana deslizante. Con un {@link RateLimitStore} (Redis) el límite vale
 * entre instancias; si no hay o no responde, se aplica la ventana en memoria de esta instancia. Pensado para endpoints
 * públicos; {@code maxTracked} acota las claves en memoria (se purgan las inactivas al superarlo). Se crean con
 * {@link RateLimiters}.
 */
public final class SlidingWindowRateLimiter {
    private final String name;
    private final RateLimitStore remote;
    private final String errorCode;
    private final int maxPerWindow;
    private final Duration window;
    private final int maxTracked;
    private final Clock clock;
    private final Map<String, Deque<Instant>> calls = new ConcurrentHashMap<>();
    private Instant lastPurge;

    /** Solo en memoria (pruebas y respaldo). */
    public SlidingWindowRateLimiter(String errorCode, int maxPerWindow, Duration window, int maxTracked, Clock clock) {
        this(errorCode, errorCode, maxPerWindow, window, maxTracked, clock, null);
    }

    /**
     * @param name   prefijo de la clave en el almacén compartido (distingue limitadores con el mismo código)
     * @param remote almacén compartido; {@code null} = solo memoria
     */
    public SlidingWindowRateLimiter(String name, String errorCode, int maxPerWindow, Duration window, int maxTracked,
            Clock clock, RateLimitStore remote) {
        this.name = name;
        this.remote = remote;
        this.errorCode = errorCode;
        this.maxPerWindow = maxPerWindow;
        this.window = window;
        this.maxTracked = maxTracked;
        this.clock = clock;
    }

    /**
     * Registra una llamada de {@code key}.
     *
     * @throws TooManyRequestsException con el código configurado si se supera el límite
     */
    public void acquire(String key) {
        Instant now = clock.instant();
        String mapKey = key == null ? "?" : key;
        if (remote != null) {
            Optional<Boolean> shared = remote.tryAcquire(name + ":" + mapKey, maxPerWindow, window, now);
            if (shared.isPresent()) {
                if (!shared.get()) {
                    throw new TooManyRequestsException(errorCode, "Demasiadas solicitudes.");
                }
                return;
            }
        }
        if (calls.size() >= maxTracked && !calls.containsKey(mapKey)) {
            purgeIfDue(now);
            if (calls.size() >= maxTracked && !calls.containsKey(mapKey)) {
                throw new TooManyRequestsException(errorCode, "Demasiadas solicitudes.");
            }
        }
        Deque<Instant> deque = calls.computeIfAbsent(mapKey, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && !deque.peekFirst().isAfter(now.minus(window))) {
                deque.pollFirst();
            }
            if (deque.size() >= maxPerWindow) {
                throw new TooManyRequestsException(errorCode, "Demasiadas solicitudes.");
            }
            deque.addLast(now);
        }
    }

    /** Purga claves inactivas como mucho una vez por ventana. */
    private synchronized void purgeIfDue(Instant now) {
        if (lastPurge != null && lastPurge.plus(window).isAfter(now)) {
            return;
        }
        lastPurge = now;
        calls.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return entry.getValue().isEmpty() || !entry.getValue().peekLast().isAfter(now.minus(window));
            }
        });
    }
}
