package com.autopartco.backend.shared.cache;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Activa el bean solo si hay Redis configurado: {@value #URL_PROPERTY} con valor ({@code REDIS_URL} en el {@code .env}
 * raíz). Sin ella la app usa las versiones en memoria y nunca intenta conectarse a Redis.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ConditionalOnRedis.RedisUrlPresent.class)
public @interface ConditionalOnRedis {
    String URL_PROPERTY = "spring.data.redis.url";

    final class RedisUrlPresent implements Condition {
        private static final java.util.concurrent.atomic.AtomicBoolean WARNED =
                new java.util.concurrent.atomic.AtomicBoolean();

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String url = context.getEnvironment().getProperty(URL_PROPERTY);
            if (url == null || url.isBlank()) {
                return false;
            }
            if (isValid(url)) {
                return true;
            }
            // Una URL mal escrita no tumba la app: se usa la caché en memoria. Nunca se registra la URL (lleva clave).
            if (WARNED.compareAndSet(false, true)) {
                org.slf4j.LoggerFactory.getLogger(ConditionalOnRedis.class).warn(
                        "REDIS_URL inválida: debe ser solo la URL redis://… o rediss://usuario:clave@host:puerto. "
                                + "Se usa la caché en memoria.");
            }
            return false;
        }

        /** {@code redis://} o {@code rediss://} con host; sin espacios (p. ej. un comando {@code redis-cli} pegado). */
        static boolean isValid(String url) {
            try {
                java.net.URI uri = new java.net.URI(url.strip());
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
                return (scheme.equals("redis") || scheme.equals("rediss")) && uri.getHost() != null;
            } catch (java.net.URISyntaxException invalid) {
                return false;
            }
        }
    }
}
