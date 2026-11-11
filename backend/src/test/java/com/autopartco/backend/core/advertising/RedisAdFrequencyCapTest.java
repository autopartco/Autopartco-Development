package com.autopartco.backend.core.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.autopartco.backend.shared.cache.CacheMetrics;
import com.autopartco.backend.shared.cache.RedisGuard;

class RedisAdFrequencyCapTest {
    private final RedisGuard guard = new RedisGuard(mock(StringRedisTemplate.class), Duration.ofSeconds(30),
            Clock.systemUTC(), CacheMetrics.NONE);
    private final UUID campaignId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-11T12:00:00Z");

    @Test
    void visitorPartOfTheKeyIsHmacWithTheTrafficSalt() {
        RedisAdFrequencyCap cap = new RedisAdFrequencyCap(guard, new InMemoryAdFrequencyCap(), "sal-a");
        RedisAdFrequencyCap otherSalt = new RedisAdFrequencyCap(guard, new InMemoryAdFrequencyCap(), "sal-b");

        String key = cap.key("ip:10.0.0.1", campaignId, AdPlacement.SEARCH_TOP, now);
        assertThat(key).startsWith(RedisAdFrequencyCap.PREFIX).doesNotContain("10.0.0.1")
                .endsWith(":" + campaignId + ":SEARCH_TOP:" + now.getEpochSecond() / 3_600);
        assertThat(cap.key("ip:10.0.0.1", campaignId, AdPlacement.SEARCH_TOP, now)).isEqualTo(key);
        assertThat(otherSalt.key("ip:10.0.0.1", campaignId, AdPlacement.SEARCH_TOP, now)).isNotEqualTo(key);
        assertThat(cap.key("ip:10.0.0.2", campaignId, AdPlacement.SEARCH_TOP, now)).isNotEqualTo(key);
    }
}
