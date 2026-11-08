package com.autopartco.backend.operations.metrics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.analytics.TrafficRecorder;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.shared.error.TooManyRequestsException;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /events/pageview} (público): visita del marketplace. Responde 204 siempre que el cuerpo sea válido,
 * aunque la ruta no se mida. Límite en memoria: {@value #MAX_PER_MINUTE} por IP y minuto (429).
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/events")
public class TrafficController {
    static final int MAX_PER_MINUTE = 60;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int MAX_TRACKED = 20_000;

    private final TrafficRecorder trafficRecorder;
    private final AccountLookup accountLookup;
    private final Clock clock;
    private final Map<String, Deque<Instant>> calls = new ConcurrentHashMap<>();

    public TrafficController(TrafficRecorder trafficRecorder, AccountLookup accountLookup) {
        this(trafficRecorder, accountLookup, Clock.systemUTC());
    }

    TrafficController(TrafficRecorder trafficRecorder, AccountLookup accountLookup, Clock clock) {
        this.trafficRecorder = trafficRecorder;
        this.accountLookup = accountLookup;
        this.clock = clock;
    }

    public record PageviewRequest(
            @Size(max = 100) String anonymousId,
            @Size(max = 500) String path,
            @Size(max = 500) String referrer
    ) {
    }

    @PostMapping("/pageview")
    public ResponseEntity<Void> pageview(@Valid @RequestBody PageviewRequest request, HttpServletRequest http, Authentication authentication) {
        acquire(http.getRemoteAddr());
        UUID accountId = authentication == null || !authentication.isAuthenticated() || authentication.getName() == null
                ? null
                : accountLookup.findByEmail(authentication.getName()).map(AccountRef::accountId).orElse(null);
        trafficRecorder.record(request.anonymousId(), request.path(), request.referrer(), http.getHeader("User-Agent"), accountId);
        return ResponseEntity.noContent().build();
    }

    void acquire(String key) {
        Instant now = clock.instant();
        if (calls.size() > MAX_TRACKED) {
            calls.entrySet().removeIf(entry -> {
                synchronized (entry.getValue()) {
                    return entry.getValue().isEmpty() || !entry.getValue().peekLast().isAfter(now.minus(WINDOW));
                }
            });
        }
        Deque<Instant> window = calls.computeIfAbsent(key == null ? "?" : key, ignored -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && !window.peekFirst().isAfter(now.minus(WINDOW))) {
                window.pollFirst();
            }
            if (window.size() >= MAX_PER_MINUTE) {
                throw new TooManyRequestsException("TRAFFIC_RATE_LIMITED", "Demasiadas solicitudes.");
            }
            window.addLast(now);
        }
    }
}
