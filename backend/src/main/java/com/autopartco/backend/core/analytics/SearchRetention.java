package com.autopartco.backend.core.analytics;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Retención de {@code marketplace_searches}: cada día a las 03:30 (America/Bogota) borra las búsquedas de más de 18
 * meses. {@code @EnableScheduling} lo activa {@code core.payments.internal.PaymentsConfig} para todo el backend.
 */
@Slf4j
@Component
@AllArgsConstructor
public class SearchRetention {
    static final String PURGE_SQL = """
            DELETE FROM marketplace_searches WHERE occurred_at < now() - interval '18 months'
            """;

    private final JdbcClient jdbcClient;

    @Scheduled(cron = "0 30 3 * * *", zone = "America/Bogota")
    public void purge() {
        try {
            int deleted = jdbcClient.sql(PURGE_SQL).update();
            log.info("Retención de búsquedas del marketplace: {} registros borrados.", deleted);
        } catch (RuntimeException failure) {
            log.warn("Falló la retención de búsquedas del marketplace: {}", failure.getClass().getSimpleName());
        }
    }
}
