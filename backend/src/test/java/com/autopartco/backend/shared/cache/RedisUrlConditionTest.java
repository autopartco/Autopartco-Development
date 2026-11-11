package com.autopartco.backend.shared.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RedisUrlConditionTest {

    @Test
    void acceptsOnlyRedisUrls() {
        assertThat(ConditionalOnRedis.RedisUrlPresent.isValid("rediss://default:clave@host.upstash.io:6379")).isTrue();
        assertThat(ConditionalOnRedis.RedisUrlPresent.isValid("redis://localhost:6379/0")).isTrue();
    }

    @Test
    void rejectsPastedCliCommandAndOtherSchemes() {
        assertThat(ConditionalOnRedis.RedisUrlPresent.isValid(
                "redis-cli --tls -u redis://default:clave@host.upstash.io:6379")).isFalse();
        assertThat(ConditionalOnRedis.RedisUrlPresent.isValid("https://host:6379")).isFalse();
        assertThat(ConditionalOnRedis.RedisUrlPresent.isValid("host.upstash.io:6379")).isFalse();
    }
}
