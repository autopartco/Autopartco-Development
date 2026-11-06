package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * «¿A qué segmentos pertenece esta persona en la plataforma y en cada almacén?» con las reglas de
 * {@link CustomerSegment} (las mismas de {@link CustomerSegments}). Una consulta por persona y caché en memoria de
 * {@value #TTL_MINUTES} minutos. {@code OCCASIONAL} no se emite (no es audiencia de promociones). Devuelve los códigos
 * como texto para que {@code core.pricing} no dependa de este módulo; el comprador lo arma {@code marketplace}.
 * Sin control de acceso: solo debe usarse para el comprador autenticado.
 */
@Service
public class SegmentMembership {
    static final long TTL_MINUTES = 10;
    static final Duration TTL = Duration.ofMinutes(TTL_MINUTES);
    static final int MAX_CACHED_PERSONS = 10_000;

    private final SegmentMembershipQueries queries;
    private final Clock clock;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    @Autowired
    public SegmentMembership(SegmentMembershipQueries queries) {
        this(queries, Clock.systemUTC());
    }

    SegmentMembership(SegmentMembershipQueries queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    /** Segmentos de la persona en la plataforma y por almacén. Sin persona, vacíos. */
    public record Memberships(Set<String> platformSegments, Map<UUID, Set<String>> storeSegments) {
        public static final Memberships NONE = new Memberships(Set.of(), Map.of());

        public Memberships {
            platformSegments = Set.copyOf(platformSegments);
            storeSegments = Map.copyOf(storeSegments);
        }
    }

    public Memberships memberships(UUID personId) {
        if (personId == null) {
            return Memberships.NONE;
        }
        Instant now = clock.instant();
        Cached cached = cache.get(personId);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.memberships();
        }
        Memberships computed = compute(personId, LocalDate.ofInstant(now, Analytics.ZONE));
        evictIfFull(now);
        cache.put(personId, new Cached(computed, now.plus(TTL)));
        return computed;
    }

    public Set<String> platformSegments(UUID personId) {
        return memberships(personId).platformSegments();
    }

    public Map<UUID, Set<String>> storeSegments(UUID personId) {
        return memberships(personId).storeSegments();
    }

    Memberships compute(UUID personId, LocalDate today) {
        List<SegmentMembershipQueries.Row> rows = queries.buyerActivity(personId,
                Analytics.startOf(today.minusDays(CustomerSegment.WINDOW_DAYS - 1L)));
        Set<String> platform = Set.of();
        Map<UUID, Set<String>> stores = new HashMap<>();
        for (SegmentMembershipQueries.Row row : rows) {
            CustomerSegment segment = segmentOf(personId, row, today);
            if (segment == null || segment == CustomerSegment.OCCASIONAL) {
                continue;
            }
            if (row.storeId() == null) {
                platform = Set.of(segment.name());
            } else {
                stores.put(row.storeId(), Set.of(segment.name()));
            }
        }
        return new Memberships(platform, stores);
    }

    /** Misma clasificación que los informes de clientes; sin ninguna compra, sin segmento. */
    private static CustomerSegment segmentOf(UUID personId, SegmentMembershipQueries.Row row, LocalDate today) {
        if (row.lastOrderAt() == null) {
            return null;
        }
        CustomerAnalyticsQueries.Activity activity = new CustomerAnalyticsQueries.Activity(personId, null,
                row.orders(), BigDecimal.ZERO, 0, row.firstOrderAt(), row.lastOrderAt());
        return CustomerSegments.classify(List.of(activity), today).get(0).segment();
    }

    private void evictIfFull(Instant now) {
        if (cache.size() < MAX_CACHED_PERSONS) {
            return;
        }
        cache.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
        if (cache.size() >= MAX_CACHED_PERSONS) {
            cache.clear();
        }
    }

    int cachedPersons() {
        return cache.size();
    }

    private record Cached(Memberships memberships, Instant expiresAt) {
    }
}
