package com.autopartco.backend.core.advertising;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.autopartco.backend.shared.cache.TtlCache;

/** {@link AdCandidateCache} en memoria con caducidad de {@value #TTL_SECONDS} s, por instancia. */
@Component
public class InMemoryAdCandidateCache implements AdCandidateCache {
    static final long TTL_SECONDS = 60;

    private final TtlCache<AdPlacement, List<AdCandidate>> cache = new TtlCache<>(TTL_SECONDS);

    @Override
    public List<AdCandidate> get(AdPlacement placement, Supplier<List<AdCandidate>> loader) {
        return cache.get(placement, () -> List.copyOf(loader.get()));
    }

    @Override
    public void invalidate() {
        cache.clear();
    }
}
