package com.autopartco.backend.core.advertising;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * {@link AdFrequencyCap} en memoria por instancia, con ventanas de hora fija (UTC). La clave guarda solo un hash del
 * visitante; al pasar de {@value #MAX_TRACKED} claves se descartan las de horas anteriores.
 */
@Component
public class InMemoryAdFrequencyCap implements AdFrequencyCap {
    static final int MAX_TRACKED = 100_000;
    private static final long HOUR_SECONDS = 3_600;

    private record Key(String visitor, UUID campaignId, AdPlacement placement, long hour) {
    }

    private final Map<Key, AtomicInteger> counters = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String visitorKey, UUID campaignId, AdPlacement placement, Instant now) {
        long hour = now.getEpochSecond() / HOUR_SECONDS;
        if (counters.size() >= MAX_TRACKED) {
            counters.keySet().removeIf(key -> key.hour() < hour);
            if (counters.size() >= MAX_TRACKED) {
                counters.clear();
            }
        }
        AtomicInteger counter = counters.computeIfAbsent(new Key(visitorKey, campaignId, placement, hour),
                ignored -> new AtomicInteger());
        while (true) {
            int current = counter.get();
            if (current >= MAX_PER_HOUR) {
                return false;
            }
            if (counter.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }
}
