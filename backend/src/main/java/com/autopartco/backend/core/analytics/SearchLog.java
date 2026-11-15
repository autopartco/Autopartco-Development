package com.autopartco.backend.core.analytics;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * Registro best-effort de búsquedas del marketplace en {@code marketplace_searches}. Escribe en un hilo propio con
 * cola acotada (si se llena, descarta) y nunca lanza: un fallo solo queda en el log, sin el texto buscado. Ignora la
 * misma búsqueda (sesión, texto normalizado y vehículo) repetida en {@value #DEDUP_MINUTES} minutos.
 * <p>
 * El identificador de cada búsqueda lo genera el controlador (se devuelve al cliente como {@code searchId} para
 * asociarle los clics) y se inserta tal cual. Cuando la deduplicación descarta la fila, el hilo de escritura busca la
 * búsqueda existente (una sola consulta, fuera de la petición) y guarda en memoria {@code id devuelto → id existente};
 * {@link #resolve(UUID)} lo aplica a los clics para que no se pierdan. El mapa es acotado (LRU) y propio de cada
 * instancia: con varias instancias, un clic que llegue a otra se descarta (best-effort, solo afecta al aprendizaje).
 */
@Slf4j
@Service
public class SearchLog {
    static final int DEDUP_MINUTES = 10;
    static final int MAX_ALIASES = 5_000;
    private static final int QUEUE_CAPACITY = 1_000;

    /** Misma búsqueda de la misma sesión dentro de la ventana de deduplicación (alias {@code m}). */
    private static final String SAME_SEARCH = """
            m.session_hash = CAST(:sessionHash AS varchar)
                     AND m.query_normalized IS NOT DISTINCT FROM CAST(:queryNormalized AS varchar)
                     AND m.make_id IS NOT DISTINCT FROM CAST(:makeId AS uuid)
                     AND m.model_id IS NOT DISTINCT FROM CAST(:modelId AS uuid)
                     AND m.version_id IS NOT DISTINCT FROM CAST(:versionId AS uuid)
                     AND m.vehicle_year IS NOT DISTINCT FROM CAST(:vehicleYear AS smallint)
                     AND m.occurred_at > now() - interval '10 minutes'""";

    static final String INSERT_SQL = """
            INSERT INTO marketplace_searches
                (id, occurred_at, session_hash, query_text, query_normalized, make_id, model_id, version_id,
                 vehicle_year, category_id, municipality_code, results_count, authenticated)
            SELECT CAST(:id AS uuid), now(), CAST(:sessionHash AS varchar), CAST(:queryText AS varchar),
                   CAST(:queryNormalized AS varchar), CAST(:makeId AS uuid), CAST(:modelId AS uuid),
                   CAST(:versionId AS uuid), CAST(:vehicleYear AS smallint), CAST(:categoryId AS uuid),
                   CAST(:municipalityCode AS char(5)), :resultsCount, :authenticated
            WHERE CAST(:sessionHash AS varchar) IS NULL
               OR NOT EXISTS (
                   SELECT 1 FROM marketplace_searches m
                   WHERE""" + " " + SAME_SEARCH + """

               )
            """;

    /** La búsqueda que deduplicó a otra: la más reciente de la misma sesión, consulta y vehículo. */
    static final String EXISTING_SQL = """
            SELECT m.id FROM marketplace_searches m
            WHERE""" + " " + SAME_SEARCH + """

            ORDER BY m.occurred_at DESC
            LIMIT 1
            """;

    private final JdbcClient jdbcClient;
    private final Executor executor;
    private final Map<UUID, UUID> aliases = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, UUID> eldest) {
            return size() > MAX_ALIASES;
        }
    };

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

    /**
     * Identificador con el que se guardó la búsqueda que el cliente conoce como {@code searchId}: el mismo, o el de la
     * búsqueda existente si la deduplicación descartó la fila.
     */
    public UUID resolve(UUID searchId) {
        if (searchId == null) {
            return null;
        }
        synchronized (aliases) {
            return aliases.getOrDefault(searchId, searchId);
        }
    }

    static Row toRow(MarketplaceSearch search, LocalDate today) {
        String municipality = search.municipalityCode() == null ? null : search.municipalityCode().strip();
        return new Row(
                search.searchId() == null ? UUID.randomUUID() : search.searchId(),
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
            int inserted = jdbcClient.sql(INSERT_SQL)
                    .param("id", text(row.id()))
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
            if (inserted == 0 && row.sessionHash() != null) {
                alias(row);
            }
        } catch (RuntimeException failure) {
            log.warn("No se pudo registrar la búsqueda del marketplace: {}", failure.getClass().getSimpleName());
        }
    }

    /** La fila se descartó por duplicada: los clics de {@code row.id()} se asocian a la búsqueda existente. */
    private void alias(Row row) {
        UUID existing = jdbcClient.sql(EXISTING_SQL)
                .param("sessionHash", row.sessionHash())
                .param("queryNormalized", row.queryNormalized())
                .param("makeId", text(row.makeId()))
                .param("modelId", text(row.modelId()))
                .param("versionId", text(row.versionId()))
                .param("vehicleYear", row.vehicleYear())
                .query(UUID.class)
                .optional()
                .orElse(null);
        if (existing != null) {
            synchronized (aliases) {
                aliases.put(row.id(), existing);
            }
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
            UUID id,
            String sessionHash,
            String queryText,
            String queryNormalized,
            UUID makeId,
            UUID modelId,
            UUID versionId,
            Integer vehicleYear,
            UUID categoryId,
            String municipalityCode,
            int resultsCount,
            boolean authenticated
    ) {
    }
}
