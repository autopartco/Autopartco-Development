package com.autopartco.backend.core.pricing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Registro best-effort en {@code promotion_exposures} (solo inserción) de los precios mostrados a un comprador
 * autenticado que ganó una promoción con audiencia distinta de {@code ALL}. Sirve para medir alcance y armar el grupo
 * de control. Escribe por lotes en un hilo propio con cola acotada (si se llena, descarta) y nunca lanza: un fallo
 * solo queda en el log, sin identificadores del comprador.
 */
@Slf4j
@Service
public class PromotionExposureRecorder {
    /** Máximo de exposiciones por llamada (un listado completo del marketplace puede ser largo). */
    static final int MAX_PER_CALL = 500;
    private static final int QUEUE_CAPACITY = 1_000;

    static final String INSERT_SQL = """
            INSERT INTO promotion_exposures (promotion_id, person_id, product_id)
            VALUES (?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Executor executor;

    @Autowired
    public PromotionExposureRecorder(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, defaultExecutor());
    }

    PromotionExposureRecorder(JdbcTemplate jdbcTemplate, Executor executor) {
        this.jdbcTemplate = jdbcTemplate;
        this.executor = executor;
    }

    /** Una promoción mostrada sobre un producto. */
    public record Exposure(UUID promotionId, UUID productId) {
    }

    /**
     * Encola las exposiciones de {@code personId} (sin repetir pares, hasta {@value #MAX_PER_CALL}). Sin persona o
     * sin exposiciones no hace nada. Nunca lanza.
     */
    public void record(UUID personId, Collection<Exposure> exposures) {
        try {
            if (personId == null || exposures == null || exposures.isEmpty()) {
                return;
            }
            List<Object[]> rows = new ArrayList<>();
            for (Exposure exposure : new LinkedHashSet<>(exposures)) {
                if (exposure == null || exposure.promotionId() == null || exposure.productId() == null) {
                    continue;
                }
                rows.add(new Object[] {exposure.promotionId(), personId, exposure.productId()});
                if (rows.size() >= MAX_PER_CALL) {
                    break;
                }
            }
            if (!rows.isEmpty()) {
                executor.execute(() -> insert(rows));
            }
        } catch (RuntimeException failure) {
            log.warn("No se pudo encolar el registro de exposiciones de promociones: {}",
                    failure.getClass().getSimpleName());
        }
    }

    void insert(List<Object[]> rows) {
        try {
            jdbcTemplate.batchUpdate(INSERT_SQL, rows);
        } catch (RuntimeException failure) {
            log.warn("No se pudieron registrar {} exposiciones de promociones: {}", rows.size(),
                    failure.getClass().getSimpleName());
        }
    }

    @PreDestroy
    void shutdown() {
        if (executor instanceof ExecutorService service) {
            service.shutdown();
        }
    }

    private static ExecutorService defaultExecutor() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "promotion-exposures");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardPolicy());
    }
}
