package com.autopartco.backend.core.analytics;

import java.time.LocalDate;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Registro best-effort de búsquedas del marketplace en {@code marketplace_searches}. Escribe en un hilo propio con
 * cola acotada (si se llena, descarta) y nunca lanza: un fallo solo queda en el log, sin el texto buscado. Ignora la
 * misma búsqueda (sesión, texto normalizado y vehículo) repetida en {@value #DEDUP_MINUTES} minutos.
 */
@Slf4j
@Service
public class SearchLog {
    static final int DEDUP_MINUTES = 10;
    private static final int QUEUE_CAPACITY = 1_000;

    static final String INSERT_SQL = """
            INSERT INTO marketplace_searches
                (occurred_at, session_hash, query_text, query_normalized, make_id, model_id, version_id,
                 vehicle_year, category_id, municipality_code, results_count, authenticated)
            SELECT now(), CAST(:sessionHash AS varchar), CAST(:queryText AS varchar),
                   CAST(:queryNormalized AS varchar), CAST(:makeId AS uuid), CAST(:modelId AS uuid),
                   CAST(:versionId AS uuid), CAST(:vehicleYear AS smallint), CAST(:categoryId AS uuid),
                   CAST(:municipalityCode AS char(5)), :resultsCount, :authenticated
            WHERE CAST(:sessionHash AS varchar) IS NULL
               OR NOT EXISTS (
                   SELECT 1 FROM marketplace_searches m
                   WHERE m.session_hash = CAST(:sessionHash AS varchar)
                     AND m.query_normalized IS NOT DISTINCT FROM CAST(:queryNormalized AS varchar)
                     AND m.make_id IS NOT DISTINCT FROM CAST(:makeId AS uuid)
                     AND m.model_id IS NOT DISTINCT FROM CAST(:modelId AS uuid)
                     AND m.version_id IS NOT DISTINCT FROM CAST(:versionId AS uuid)
                     AND m.vehicle_year IS NOT DISTINCT FROM CAST(:vehicleYear AS smallint)
                     AND m.occurred_at > now() - interval '10 minutes'
               )
            """;

    private final JdbcClient jdbcClient;
    private final Executor executor;

    @Autowired
    public SearchLog(JdbcClient jdbcClient) {
        this(jdbcClient, defaultExecutor());
    }

    SearchLog(JdbcClient jdbcClient, Executor executor) {
        this.jdbcClient = jdbcClient;
        this.executor = executor;
    }

    /** Encola el registro si la búsqueda tiene criterio. Nunca lanza. */
    public void record(MarketplaceSearch search) {
        try {
            if (search == null || !search.hasCriteria()) {
                return;
            }
            Row row = toRow(search, Analytics.today());
            executor.execute(() -> insert(row));
        } catch (RuntimeException failure) {
            log.warn("No se pudo encolar el registro de la búsqueda del marketplace: {}",
                    failure.getClass().getSimpleName());
        }
    }

    static Row toRow(MarketplaceSearch search, LocalDate today) {
        String municipality = search.municipalityCode() == null ? null : search.municipalityCode().strip();
        return new Row(
                SearchText.sessionHash(search.sessionId(), today),
                SearchText.clean(search.queryText()),
                SearchText.normalize(search.queryText()),
                search.makeId(),
                search.modelId(),
                search.versionId(),
                search.vehicleYear(),
                search.categoryId(),
                municipality != null && municipality.length() == 5 ? municipality : null,
                Math.max(0, search.resultsCount()),
                search.authenticated());
    }

    void insert(Row row) {
        try {
            jdbcClient.sql(INSERT_SQL)
                    .param("sessionHash", row.sessionHash())
                    .param("queryText", row.queryText())
                    .param("queryNormalized", row.queryNormalized())
                    .param("makeId", text(row.makeId()))
                    .param("modelId", text(row.modelId()))
                    .param("versionId", text(row.versionId()))
                    .param("vehicleYear", row.vehicleYear())
                    .param("categoryId", text(row.categoryId()))
                    .param("municipalityCode", row.municipalityCode())
                    .param("resultsCount", row.resultsCount())
                    .param("authenticated", row.authenticated())
                    .update();
        } catch (RuntimeException failure) {
            log.warn("No se pudo registrar la búsqueda del marketplace: {}", failure.getClass().getSimpleName());
        }
    }

    @PreDestroy
    void shutdown() {
        if (executor instanceof ExecutorService service) {
            service.shutdown();
        }
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static ExecutorService defaultExecutor() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "marketplace-search-log");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardPolicy());
    }

    record Row(
            String sessionHash,
            String queryText,
            String queryNormalized,
            java.util.UUID makeId,
            java.util.UUID modelId,
            java.util.UUID versionId,
            Integer vehicleYear,
            java.util.UUID categoryId,
            String municipalityCode,
            int resultsCount,
            boolean authenticated
    ) {
    }
}
