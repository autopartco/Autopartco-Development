package com.autopartco.backend.core.analytics;

import java.util.UUID;
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
 * Registro best-effort de los clics en resultados de búsqueda ({@code search_clicks}). Como {@link SearchLog}, escribe
 * en un hilo propio con cola acotada (si se llena, descarta) y nunca lanza ni bloquea la respuesta. Un clic de una
 * búsqueda o un producto que no existe (o de un producto borrado) se descarta sin error y queda en el log a nivel
 * debug. Cada producto cuenta una sola vez por búsqueda. No guarda datos personales: solo búsqueda, producto y posición.
 */
@Slf4j
@Service
public class SearchClickLog {
    private static final int QUEUE_CAPACITY = 1_000;

    /** Inserta solo si la búsqueda y el producto existen y el producto aún no tiene clic en esa búsqueda. */
    static final String INSERT_SQL = """
            INSERT INTO search_clicks (search_id, product_id, position)
            SELECT s.id, p.id, :position
            FROM marketplace_searches s
            JOIN products p ON p.id = CAST(:productId AS uuid) AND p.deleted_at IS NULL
            WHERE s.id = CAST(:searchId AS uuid)
              AND NOT EXISTS (
                  SELECT 1 FROM search_clicks c WHERE c.search_id = s.id AND c.product_id = p.id)
            """;

    private final JdbcClient jdbcClient;
    private final SearchLog searchLog;
    private final Executor executor;

    @Autowired
    public SearchClickLog(JdbcClient jdbcClient, SearchLog searchLog) {
        this(jdbcClient, searchLog, defaultExecutor());
    }

    SearchClickLog(JdbcClient jdbcClient, SearchLog searchLog, Executor executor) {
        this.jdbcClient = jdbcClient;
        this.searchLog = searchLog;
        this.executor = executor;
    }

    /** Encola el clic. Nunca lanza. */
    public void record(UUID searchId, UUID productId, int position) {
        try {
            if (searchId == null || productId == null || position < 0) {
                return;
            }
            executor.execute(() -> insert(searchId, productId, position));
        } catch (RuntimeException failure) {
            log.warn("No se pudo encolar el clic de la búsqueda: {}", failure.getClass().getSimpleName());
        }
    }

    void insert(UUID searchId, UUID productId, int position) {
        try {
            // Si la búsqueda se deduplicó, el clic va a la búsqueda existente de la misma sesión y consulta.
            UUID target = searchLog.resolve(searchId);
            int inserted = jdbcClient.sql(INSERT_SQL)
                    .param("searchId", target.toString())
                    .param("productId", productId.toString())
                    .param("position", position)
                    .update();
            if (inserted == 0) {
                log.debug("Clic de búsqueda descartado (búsqueda o producto inexistente, o ya contado).");
            }
        } catch (RuntimeException failure) {
            log.warn("No se pudo registrar el clic de la búsqueda: {}", failure.getClass().getSimpleName());
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
                    Thread thread = new Thread(runnable, "marketplace-search-click-log");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardPolicy());
    }
}
