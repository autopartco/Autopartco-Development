package com.autopartco.backend.core.advertising;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Estados por fecha (zona {@code America/Bogota}): cada día a las 00:01 y al arrancar, {@code SCHEDULED} → {@code ACTIVE}
 * cuando llega el inicio y {@code SCHEDULED}/{@code ACTIVE}/{@code PAUSED} → {@code FINISHED} cuando pasó el fin. Un
 * fallo se registra y lo corrige la siguiente ejecución. {@code @EnableScheduling} lo activa
 * {@code core.payments.internal.PaymentsConfig}.
 */
@Slf4j
@Component
public class AdCampaignJobs {
    static final String FINISH_SQL = """
            update ad_campaigns set status = 'FINISHED', updated_at = now(), version = version + 1
            where status in ('SCHEDULED', 'ACTIVE', 'PAUSED') and ends_on < :today
            """;
    static final String ACTIVATE_SQL = """
            update ad_campaigns set status = 'ACTIVE', updated_at = now(), version = version + 1
            where status = 'SCHEDULED' and starts_on <= :today and ends_on >= :today
            """;

    private final JdbcClient jdbcClient;
    private final AdCandidateCache candidateCache;
    private final Clock clock;

    @Autowired
    public AdCampaignJobs(JdbcClient jdbcClient, AdCandidateCache candidateCache) {
        this(jdbcClient, candidateCache, Clock.systemUTC());
    }

    AdCampaignJobs(JdbcClient jdbcClient, AdCandidateCache candidateCache, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.candidateCache = candidateCache;
        this.clock = clock;
    }

    @Scheduled(cron = "0 1 0 * * *", zone = "America/Bogota")
    public void daily() {
        runSafely();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        runSafely();
    }

    private void runSafely() {
        try {
            Result result = advance();
            if (result.finished() + result.activated() > 0) {
                log.info("Ad campaigns advanced by date: {} finished, {} activated",
                        result.finished(), result.activated());
            }
        } catch (RuntimeException failure) {
            log.warn("Ad campaign date job failed: {}", failure.getClass().getSimpleName());
        }
    }

    record Result(int finished, int activated) {
    }

    /** Aplica las transiciones por fecha de hoy (cada UPDATE es atómico) y vacía la caché de candidatos. */
    Result advance() {
        LocalDate today = AdText.today(clock);
        int finished = jdbcClient.sql(FINISH_SQL).param("today", today).update();
        int activated = jdbcClient.sql(ACTIVATE_SQL).param("today", today).update();
        candidateCache.invalidate();
        return new Result(finished, activated);
    }
}
