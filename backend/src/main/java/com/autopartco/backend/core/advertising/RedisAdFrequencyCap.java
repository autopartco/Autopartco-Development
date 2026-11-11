package com.autopartco.backend.core.advertising;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.autopartco.backend.shared.cache.ConditionalOnRedis;
import com.autopartco.backend.shared.cache.RedisGuard;

/**
 * {@link AdFrequencyCap} compartido en Redis ({@code ads:freq}): un contador por
 * {@code visitorHash:campaña:ubicación:hora} con {@code INCR} y caducidad de 1 h, común a todas las instancias. El
 * visitante se guarda solo como hash SHA-256 truncado. Si Redis no responde, decide {@link InMemoryAdFrequencyCap}.
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

    public RedisAdFrequencyCap(RedisGuard redis, InMemoryAdFrequencyCap fallback) {
        this.redis = redis;
        this.fallback = fallback;
    }

    @Override
    public boolean tryAcquire(String visitorKey, UUID campaignId, AdPlacement placement, Instant now) {
        String key = PREFIX + hash(visitorKey) + ':' + campaignId + ':' + placement.name() + ':'
                + now.getEpochSecond() / HOUR_SECONDS;
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

    static String hash(String visitorKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(visitorKey).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
