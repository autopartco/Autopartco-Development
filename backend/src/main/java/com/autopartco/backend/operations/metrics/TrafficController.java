package com.autopartco.backend.operations.metrics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
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

import com.autopartco.backend.core.advertising.AdEvents;
import com.autopartco.backend.core.analytics.TrafficRecorder;
import com.autopartco.backend.core.analytics.VisibilityRecorder;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.shared.error.TooManyRequestsException;
import com.autopartco.backend.shared.web.ApiPaths;

import com.autopartco.backend.shared.web.VisitorCookie;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final AdEvents adEvents;
    private final Clock clock;
    private final Map<String, Deque<Instant>> calls = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> storeCalls = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> storeDailyCalls = new ConcurrentHashMap<>();

    @Autowired
    public TrafficController(TrafficRecorder trafficRecorder, VisibilityRecorder visibilityRecorder,
            AccountLookup accountLookup, AdEvents adEvents) {
        this(trafficRecorder, visibilityRecorder, accountLookup, adEvents, Clock.systemUTC());
    }

    TrafficController(TrafficRecorder trafficRecorder, VisibilityRecorder visibilityRecorder,
            AccountLookup accountLookup, AdEvents adEvents, Clock clock) {
        this.trafficRecorder = trafficRecorder;
        this.visibilityRecorder = visibilityRecorder;
        this.accountLookup = accountLookup;
        this.adEvents = adEvents;
        this.clock = clock;
    }

    public record PageviewRequest(
            @Size(max = 100) String anonymousId,
            @Size(max = 500) String path,
            @Size(max = 500) String referrer
    ) {
    }

    @PostMapping("/pageview")
    public ResponseEntity<Void> pageview(@Valid @RequestBody PageviewRequest request, HttpServletRequest http,
            HttpServletResponse response, Authentication authentication) {
        acquire(http.getRemoteAddr());
        UUID accountId = authentication == null || !authentication.isAuthenticated() || authentication.getName() == null
                ? null
                : accountLookup.findByEmail(authentication.getName()).map(AccountRef::accountId).orElse(null);
        trafficRecorder.record(anonymousId(request.anonymousId(), http, response), request.path(), request.referrer(),
                http.getHeader("User-Agent"), accountId);
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
            @Size(max = 10) String channel,
            UUID branchId,
            UUID serviceId
    ) {
    }

    /**
     * Visita al perfil o a una publicación, compartido, o clic de WhatsApp o «Cómo llegar» (estos con {@code branchId}
     * del almacén; {@code productId} y {@code serviceId} opcionales), para las métricas del almacén. Siempre 204 (lo inválido
     * se ignora); comparte el límite por IP con {@code /pageview}. Nunca guarda la cuenta.
     */
    @PostMapping("/visibility")
    public ResponseEntity<Void> visibility(@Valid @RequestBody VisibilityRequest request, HttpServletRequest http,
            HttpServletResponse response) {
        acquire(http.getRemoteAddr());
        if (request.storeId() != null) {
            acquireStore(http.getRemoteAddr(), request.storeId());
        }
        visibilityRecorder.record(new VisibilityRecorder.Visit(anonymousId(request.anonymousId(), http, response),
                request.kind(),
                request.storeId(), request.productId(), request.ref(), request.utmSource(), request.referrer(),
                request.channel(), request.branchId(), request.serviceId()));
        return ResponseEntity.noContent().build();
    }

    /**
     * Evento de anuncio. Una impresión, clic, clic de WhatsApp o «agregar al carrito» con {@code token}, o un lote de
     * impresiones con {@code tokens} (máx. {@value AdEvents#MAX_BATCH}; se suman a {@code token} si llegan ambos).
     * {@code kind}: {@code IMPRESSION|CLICK|WHATSAPP_CLICK|ADD_TO_CART}. Sin validación de forma a propósito: lo que no
     * sirve se ignora (tokens de más de 400 caracteres, más de {@value AdEvents#MAX_BATCH} tokens, {@code anonymousId}
     * de más de 100).
     */
    public record AdEventRequest(String token, List<String> tokens, String kind, String anonymousId) {
    }

    /**
     * Registra eventos de anuncios. Siempre 204 (salvo 429 por el límite o un JSON ilegible): un token falso, alterado
     * o vencido, un tipo no permitido o un visitante sin id se ignoran. Comparte el límite por IP con
     * {@code /pageview} y {@code /visibility} (un lote cuenta como una llamada). Con sesión, el visitante es la cuenta
     * (para atribuir pedidos); sin ella, {@code anonymousId}. Nunca guarda la IP ni la cuenta en claro.
     */
    @PostMapping("/ads")
    public ResponseEntity<Void> ads(@RequestBody AdEventRequest request, HttpServletRequest http,
            HttpServletResponse response, Authentication authentication) {
        acquire(http.getRemoteAddr());
        String anonymousId = anonymousId(request.anonymousId(), http, response);
        List<String> tokens = new ArrayList<>();
        if (request.token() != null) {
            tokens.add(request.token());
        }
        if (request.tokens() != null) {
            request.tokens().stream().filter(java.util.Objects::nonNull).limit(AdEvents.MAX_BATCH)
                    .forEach(tokens::add);
        }
        if (!tokens.isEmpty()) {
            UUID accountId = authentication == null || !authentication.isAuthenticated()
                    || authentication.getName() == null
                    ? null
                    : accountLookup.findByEmail(authentication.getName()).map(AccountRef::accountId).orElse(null);
            adEvents.record(tokens, request.kind(), anonymousId, accountId);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Visitante anónimo: el {@code anonymousId} del cuerpo tiene prioridad (compatibilidad con clientes que aún lo
     * envían); si no llega, la cookie {@code ap_aid}, que se emite aquí cuando falta.
     */
    static String anonymousId(String fromBody, HttpServletRequest http, HttpServletResponse response) {
        UUID cookie = VisitorCookie.ensure(http, response);
        return fromBody != null && !fromBody.isBlank() ? fromBody : cookie.toString();
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
