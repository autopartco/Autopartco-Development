package com.autopartco.backend.core.advertising;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.autopartco.backend.shared.cache.AppCaches;
import com.autopartco.backend.shared.cache.ConditionalOnRedis;
import com.autopartco.backend.shared.cache.ResilientCacheManager;
import com.autopartco.backend.shared.cache.TypedCache;

import tools.jackson.core.type.TypeReference;

/**
 * {@link AdCandidateCache} compartida en Redis ({@code ads:active}, 60 s por ubicación). Solo con {@code REDIS_URL};
 * si Redis no responde, esa llamada usa la copia local. Guarda candidatos (campaña, producto, segmentación), nunca
 * anuncios elegidos ni tokens. {@link #invalidate()} borra la caché en Redis, así que vale para todas las instancias.
 */
@Component
@Primary
@ConditionalOnRedis
public class RedisAdCandidateCache implements AdCandidateCache {
    private final TypedCache<List<AdCandidate>> cache;

    public RedisAdCandidateCache(ResilientCacheManager caches) {
        this.cache = caches.typed(AppCaches.ADS_ACTIVE, new TypeReference<List<AdCandidate>>() {
        }.getType());
    }

    @Override
    public List<AdCandidate> get(AdPlacement placement, Supplier<List<AdCandidate>> loader) {
        return cache.get(placement.name(), () -> List.copyOf(loader.get()));
    }

    @Override
    public void invalidate() {
        cache.clear();
    }
}
