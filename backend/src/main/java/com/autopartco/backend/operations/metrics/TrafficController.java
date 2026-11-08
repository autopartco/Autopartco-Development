package com.autopartco.backend.operations.metrics;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.analytics.TrafficRecorder;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.SlidingWindowRateLimiter;

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
    private static final int MAX_TRACKED = 20_000;

    private final TrafficRecorder trafficRecorder;
    private final AccountLookup accountLookup;
    private final SlidingWindowRateLimiter rateLimiter;

    @Autowired
    public TrafficController(TrafficRecorder trafficRecorder, AccountLookup accountLookup) {
        this(trafficRecorder, accountLookup, Clock.systemUTC());
    }

    TrafficController(TrafficRecorder trafficRecorder, AccountLookup accountLookup, Clock clock) {
        this.trafficRecorder = trafficRecorder;
        this.accountLookup = accountLookup;
        this.rateLimiter = new SlidingWindowRateLimiter("TRAFFIC_RATE_LIMITED", MAX_PER_MINUTE, Duration.ofMinutes(1),
                MAX_TRACKED, clock);
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
        rateLimiter.acquire(key);
    }
}
