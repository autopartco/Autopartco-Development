package com.autopartco.backend.core.search;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Diccionarios para interpretar la consulta: marcas y modelos vigentes, municipios DIVIPOLA, sinónimos y el léxico del
 * corrector ortográfico (palabras del índice con su frecuencia). Se cargan de
 * la BD y se guardan 1 h en memoria (no usa {@code TtlCache}: esa caché se vacía en cada escritura del catálogo). Si
 * la carga falla se sigue con el último vocabulario (o uno vacío): interpretar nunca rompe la búsqueda.
 */
@Slf4j
@Component
public class SearchDictionary {
    static final long TTL_NANOS = 3_600_000_000_000L;

    static final String MAKES_SQL = "select id, name from make where deleted_at is null";
    static final String MODELS_SQL = "select id, make_id, name from model where deleted_at is null";
    static final String CITIES_SQL = """
            select code, name, is_department_capital, coalesce(population, 0) as population
            from geo_municipalities
            """;
    static final String SYNONYMS_SQL = "select term, expands_to from search_synonyms";
    /**
     * Léxico del corrector ortográfico en una sola consulta agregada: cada palabra de letras de {@code search_text}
     * (ya en minúsculas y sin tildes) con el número de listados que la contienen, más las palabras de los códigos de
     * categoría y de los nombres de fabricante (frecuencia 1 por aparición). Marcas, modelos y sinónimos se añaden en
     * memoria desde el resto del vocabulario. {@code ts_stat} con la configuración {@code simple} (sin raíces ni
     * palabras vacías) cuenta los documentos por palabra en C; medido en la evaluación, escala mejor que
     * {@code regexp_split_to_table} + {@code group by} (la mitad del tiempo con el catálogo ×10).
     */
    static final String LEXICON_SQL = """
            select word, sum(n)::int as frequency
            from (
                select s.word, s.ndoc as n
                from ts_stat('select to_tsvector(''simple'', search_text) from search_listings') s
                union all
                select regexp_split_to_table(public.immutable_unaccent(lower(c.code)), '[^a-z]+'), 1
                from categories c where c.deleted_at is null
                union all
                select regexp_split_to_table(public.immutable_unaccent(lower(m.name)), '[^a-z]+'), 1
                from manufacturers m where m.deleted_at is null
            ) t
            where word ~ '^[a-z]{3,30}$'
            group by word
            """;

    public record Make(UUID id, String name) {
    }

    public record Model(UUID id, UUID makeId, String name) {
    }

    public record City(String code, String name, boolean capital, long population) {
    }

    /**
     * Vocabulario completo; {@code synonyms} con claves y valores ya normalizados; {@code lexicon}: palabra del catálogo
     * (solo {@code [a-z]}) → número de listados que la contienen, para {@link SpellCorrector}.
     */
    public record Vocabulary(List<Make> makes, List<Model> models, List<City> cities,
            Map<String, List<String>> synonyms, Map<String, Integer> lexicon) {
        public static final Vocabulary EMPTY = new Vocabulary(List.of(), List.of(), List.of(), Map.of(), Map.of());

        /** Sin léxico del catálogo. */
        public Vocabulary(List<Make> makes, List<Model> models, List<City> cities,
                Map<String, List<String>> synonyms) {
            this(makes, models, cities, synonyms, Map.of());
        }

        public Vocabulary {
            lexicon = lexicon == null ? Map.of() : lexicon;
        }
    }

    private final JdbcClient jdbcClient;
    private volatile Vocabulary cached;
    private volatile long loadedAt;

    public SearchDictionary(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Vocabulary vocabulary() {
        Vocabulary current = cached;
        if (current != null && System.nanoTime() - loadedAt < TTL_NANOS) {
            return current;
        }
        synchronized (this) {
            if (cached != null && System.nanoTime() - loadedAt < TTL_NANOS) {
                return cached;
            }
            try {
                cached = load();
            } catch (RuntimeException failure) {
                log.warn("No se pudo cargar el vocabulario de búsqueda: {}", failure.getClass().getSimpleName());
                if (cached == null) {
                    cached = Vocabulary.EMPTY;
                }
            }
            loadedAt = System.nanoTime();
            return cached;
        }
    }

    Vocabulary load() {
        List<Make> makes = jdbcClient.sql(MAKES_SQL)
                .query((rs, row) -> new Make(rs.getObject("id", UUID.class), rs.getString("name")))
                .list();
        List<Model> models = jdbcClient.sql(MODELS_SQL)
                .query((rs, row) -> new Model(rs.getObject("id", UUID.class), rs.getObject("make_id", UUID.class),
                        rs.getString("name")))
                .list();
        List<City> cities = jdbcClient.sql(CITIES_SQL)
                .query((rs, row) -> new City(rs.getString("code"), rs.getString("name"),
                        rs.getBoolean("is_department_capital"), rs.getLong("population")))
                .list();
        Map<String, List<String>> synonyms = new LinkedHashMap<>();
        jdbcClient.sql(SYNONYMS_SQL).query((rs, row) -> {
            String term = QueryParser.normalizeName(rs.getString("term"));
            List<String> expansions = textArray(rs.getArray("expands_to")).stream()
                    .map(QueryParser::normalizeName)
                    .filter(value -> value != null && !value.equals(term))
                    .toList();
            if (term != null && !expansions.isEmpty()) {
                synonyms.merge(term, expansions, (first, second) -> java.util.stream.Stream
                        .concat(first.stream(), second.stream()).distinct().toList());
            }
            return term;
        }).list();
        Map<String, Integer> lexicon = new HashMap<>();
        jdbcClient.sql(LEXICON_SQL).query((rs, row) -> {
            String word = rs.getString("word");
            if (word != null && !word.isEmpty()) {
                lexicon.merge(word, rs.getInt("frequency"), Integer::sum);
            }
            return word;
        }).list();
        return new Vocabulary(makes, models, cities, Map.copyOf(synonyms), lexicon);
    }

    private static List<String> textArray(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object value = array.getArray();
        return value instanceof Object[] items
                ? Arrays.stream(items).filter(item -> item != null).map(Object::toString).toList()
                : List.of();
    }
}
