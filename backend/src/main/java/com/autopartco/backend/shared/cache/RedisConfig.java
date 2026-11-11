package com.autopartco.backend.shared.cache;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;

/**
 * Conexión a Redis (Lettuce) solo si hay {@code spring.data.redis.url} ({@code REDIS_URL}). La autoconfiguración de
 * Spring Boot para Redis está excluida en {@code application.yaml} para que, sin URL, nunca se intente
 * {@code localhost:6379} ni aparezca Redis en {@code /actuator/health}. La conexión es perezosa: la app arranca aunque
 * Redis no responda. Tiempos de espera: conexión {@code spring.data.redis.connect-timeout} (500 ms) y comando
 * {@code spring.data.redis.timeout} (200 ms); sin conexión los comandos fallan de inmediato.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnRedis
public class RedisConfig {

    @Bean
    LettuceConnectionFactory redisConnectionFactory(
            @Value("${spring.data.redis.url}") String url,
            @Value("${spring.data.redis.connect-timeout:500ms}") String connectTimeoutText,
            @Value("${spring.data.redis.timeout:200ms}") String commandTimeoutText) {
        Duration connectTimeout = DurationStyle.detectAndParse(connectTimeoutText.strip());
        Duration commandTimeout = DurationStyle.detectAndParse(commandTimeoutText.strip());
        URI uri = URI.create(url.strip());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        if (!scheme.equals("redis") && !scheme.equals("rediss")) {
            throw new IllegalStateException("REDIS_URL debe empezar por redis:// o rediss://");
        }
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(uri.getHost(),
                uri.getPort() > 0 ? uri.getPort() : 6379);
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null && !userInfo.isEmpty()) {
            int colon = userInfo.indexOf(':');
            String user = colon < 0 ? null : decode(userInfo.substring(0, colon));
            String password = decode(colon < 0 ? userInfo : userInfo.substring(colon + 1));
            if (user != null && !user.isBlank()) {
                server.setUsername(user);
            }
            if (!password.isEmpty()) {
                server.setPassword(password);
            }
        }
        String path = uri.getPath();
        if (path != null && path.length() > 1) {
            server.setDatabase(Integer.parseInt(path.substring(1)));
        }
        ClientOptions options = ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build())
                .timeoutOptions(TimeoutOptions.enabled(commandTimeout))
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .autoReconnect(true)
                .build();
        LettuceClientConfiguration.LettuceClientConfigurationBuilder client = LettuceClientConfiguration.builder()
                .commandTimeout(commandTimeout)
                .shutdownTimeout(Duration.ofMillis(100))
                .clientOptions(options);
        if (scheme.equals("rediss")) {
            client.useSsl();
        }
        return new LettuceConnectionFactory(server, client.build());
    }

    @Bean
    StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }

    @Bean
    RedisGuard redisGuard(StringRedisTemplate stringRedisTemplate, CacheMetrics cacheMetrics,
            @Value("${app.cache.redis.retry-after:30s}") String retryAfter) {
        return new RedisGuard(stringRedisTemplate, DurationStyle.detectAndParse(retryAfter.strip()), Clock.systemUTC(),
                cacheMetrics);
    }

    @Bean
    RedisRateLimitStore redisRateLimitStore(RedisGuard redisGuard,
            @Value("${app.traffic.salt:${app.public-base-url:autopartco}}") String salt) {
        return new RedisRateLimitStore(redisGuard, new KeyHmac(salt));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
