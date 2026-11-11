package com.autopartco.backend.core.advertising;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.autopartco.backend.shared.cache.ConditionalOnRedis;
import com.autopartco.backend.shared.cache.KeyHmac;
import com.autopartco.backend.shared.cache.RedisGuard;

/**
 * {@link AdFrequencyCap} compartido en Redis ({@code ads:freq}): un contador por
 * {@code visitorHash:campaña:ubicación:hora} con {@code INCR} y caducidad de 1 h, común a todas las instancias. El
 * visitante va solo como HMAC-SHA256 con la sal de tráfico ({@link KeyHmac}). Si Redis no responde, decide
 * {@link InMemoryAdFrequencyCap}.
 */
@Component
@Primary
@ConditionalOnRedis
public class RedisAdFrequencyCap implements AdFrequencyCap {
    static final String PREFIX = "ap:ads:freq:";
    static final Duration TTL = Duration.ofHours(1);
    private static final long HOUR_SECONDS = 3_600;

    private final RedisGuard redis;
    private final InMemoryAdFrequencyCap fallback;
    private final KeyHmac hmac;

    public RedisAdFrequencyCap(RedisGuard redis, InMemoryAdFrequencyCap fallback,
            @Value("${app.traffic.salt:${app.public-base-url:autopartco}}") String salt) {
        this.redis = redis;
        this.fallback = fallback;
        this.hmac = new KeyHmac(salt);
    }

    @Override
    public boolean tryAcquire(String visitorKey, UUID campaignId, AdPlacement placement, Instant now) {
        String key = key(visitorKey, campaignId, placement, now);
        Optional<Long> count = redis.call("ads-freq", r -> {
            Long value = r.opsForValue().increment(key);
            if (value != null && value == 1L) {
                r.expire(key, TTL);
            }
            return value;
        });
        if (count.isEmpty()) {
            return fallback.tryAcquire(visitorKey, campaignId, placement, now);
        }
        return count.get() <= MAX_PER_HOUR;
    }

    String key(String visitorKey, UUID campaignId, AdPlacement placement, Instant now) {
        return PREFIX + hmac.hash(visitorKey) + ':' + campaignId + ':' + placement.name() + ':'
                + now.getEpochSecond() / HOUR_SECONDS;
    }
}
