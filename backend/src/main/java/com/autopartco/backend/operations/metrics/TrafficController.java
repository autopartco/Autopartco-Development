package com.autopartco.backend.operations.metrics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.analytics.TrafficRecorder;
import com.autopartco.backend.core.analytics.VisibilityRecorder;
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
    /** Por IP y almacén en {@code /visibility}: frena inflar las métricas de una tienda rotando el id anónimo. */
    static final int MAX_STORE_PER_MINUTE = 20;
    static final int MAX_STORE_PER_DAY = 300;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Duration DAY = Duration.ofDays(1);
    private static final int MAX_TRACKED = 20_000;

    private final TrafficRecorder trafficRecorder;
    private final VisibilityRecorder visibilityRecorder;
    private final AccountLookup accountLookup;
    private final Clock clock;
    private final Map<String, Deque<Instant>> calls = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> storeCalls = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> storeDailyCalls = new ConcurrentHashMap<>();

    @Autowired
    public TrafficController(
            TrafficRecorder trafficRecorder, VisibilityRecorder visibilityRecorder, AccountLookup accountLookup) {
        this(trafficRecorder, visibilityRecorder, accountLookup, Clock.systemUTC());
    }

    TrafficController(
            TrafficRecorder trafficRecorder, VisibilityRecorder visibilityRecorder, AccountLookup accountLookup,
            Clock clock) {
        this.trafficRecorder = trafficRecorder;
        this.visibilityRecorder = visibilityRecorder;
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

    public record VisibilityRequest(
            @Size(max = 100) String anonymousId,
            @Size(max = 20) String kind,
            UUID storeId,
            UUID productId,
            @Size(max = 10) String ref,
            @Size(max = 100) String utmSource,
            @Size(max = 500) String referrer,
            @Size(max = 10) String channel
    ) {
    }

    /**
     * Visita al perfil o a una publicación, o un compartido, para las métricas del almacén. Siempre 204 (lo inválido
     * se ignora); comparte el límite por IP con {@code /pageview}. Nunca guarda la cuenta.
     */
    @PostMapping("/visibility")
    public ResponseEntity<Void> visibility(@Valid @RequestBody VisibilityRequest request, HttpServletRequest http) {
        acquire(http.getRemoteAddr());
        if (request.storeId() != null) {
            acquireStore(http.getRemoteAddr(), request.storeId());
        }
        visibilityRecorder.record(new VisibilityRecorder.Visit(request.anonymousId(), request.kind(),
                request.storeId(), request.productId(), request.ref(), request.utmSource(), request.referrer(),
                request.channel()));
        return ResponseEntity.noContent().build();
    }

    void acquire(String key) {
        take(calls, key == null ? "?" : key, MAX_PER_MINUTE, WINDOW);
    }

    /** Límite por (IP, almacén) por minuto y por día. */
    void acquireStore(String ip, UUID storeId) {
        String key = (ip == null ? "?" : ip) + "|" + storeId;
        take(storeCalls, key, MAX_STORE_PER_MINUTE, WINDOW);
        take(storeDailyCalls, key, MAX_STORE_PER_DAY, DAY);
    }

    private void take(Map<String, Deque<Instant>> buckets, String key, int limit, Duration window) {
        Instant now = clock.instant();
        if (buckets.size() > MAX_TRACKED) {
            buckets.entrySet().removeIf(entry -> {
                synchronized (entry.getValue()) {
                    return entry.getValue().isEmpty() || !entry.getValue().peekLast().isAfter(now.minus(window));
                }
            });
        }
        Deque<Instant> calls = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (calls) {
            while (!calls.isEmpty() && !calls.peekFirst().isAfter(now.minus(window))) {
                calls.pollFirst();
            }
            if (calls.size() >= limit) {
                throw new TooManyRequestsException("TRAFFIC_RATE_LIMITED", "Demasiadas solicitudes.");
            }
            calls.addLast(now);
        }
    }
}
