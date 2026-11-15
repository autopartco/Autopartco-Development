package com.autopartco.backend.core.search;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.autopartco.backend.core.analytics.SearchText;

import lombok.extern.slf4j.Slf4j;

/**
 * Categoría que los compradores eligen para cada consulta, aprendida de los clics ({@code search_query_category}, que
 * recalcula {@link QueryLearningJob}). Mapa en memoria {@code query_normalized → category_id}: buscar es una consulta a
 * un {@code HashMap}, sin SQL por búsqueda. Se carga con una sola sentencia, se renueva cada hora (como
 * {@link SearchDictionary}) y al terminar el job nocturno ({@link #reload()}). Si la carga falla se sigue con el último
 * mapa (o uno vacío): el aprendizaje nunca rompe la búsqueda.
 * <p>
 * {@code clicks} cuenta sesiones distintas ({@link QueryLearningJob}), no clics. Gana la categoría con al menos
 * {@value #MIN_CLICKS} sesiones y al menos el {@code 50 %} de las de la consulta; si dos categorías empatan en el primer
 * puesto, no gana ninguna.
 */
@Slf4j
@Component
public class QueryLearning {
    static final long TTL_NANOS = 3_600_000_000_000L;
    static final int MIN_CLICKS = 5;

    static final String WINNERS_SQL = """
            select normalized_query, category_id
            from (
                select normalized_query, category_id, clicks,
                       sum(clicks) over (partition by normalized_query) as total,
                       row_number() over (partition by normalized_query order by clicks desc, category_id) as rnk,
                       lead(clicks) over (partition by normalized_query order by clicks desc, category_id) as nxt
                from search_query_category
                where clicks > 0
            ) t
            where rnk = 1 and clicks >= %d and clicks * 2 >= total and (nxt is null or nxt < clicks)
            """.formatted(MIN_CLICKS);

    private final JdbcClient jdbcClient;
    private volatile Map<String, UUID> winners;
    private volatile long loadedAt;

    public QueryLearning(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Categoría ganadora de la consulta (se normaliza como {@code marketplace_searches.query_normalized}). */
    public Optional<UUID> categoryFor(String query) {
        String key = SearchText.normalize(query);
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(current().get(key));
    }

    /** Vuelve a leer el mapa (lo llama el job al terminar y el TTL). */
    public synchronized void reload() {
        try {
            winners = load();
        } catch (RuntimeException failure) {
            log.warn("No se pudo cargar el aprendizaje de consultas: {}", failure.getClass().getSimpleName());
            if (winners == null) {
                winners = Map.of();
            }
        }
        loadedAt = System.nanoTime();
    }

    private Map<String, UUID> current() {
        Map<String, UUID> map = winners;
        if (map != null && System.nanoTime() - loadedAt < TTL_NANOS) {
            return map;
        }
        synchronized (this) {
            if (winners == null || System.nanoTime() - loadedAt >= TTL_NANOS) {
                reload();
            }
            return winners;
        }
    }

    Map<String, UUID> load() {
        Map<String, UUID> loaded = new HashMap<>();
        jdbcClient.sql(WINNERS_SQL).query((rs, row) -> {
            loaded.put(rs.getString("normalized_query"), rs.getObject("category_id", UUID.class));
            return null;
        }).list();
        return Map.copyOf(loaded);
    }
}
