package com.autopartco.backend.core.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.autopartco.backend.core.catalog.ListingKey;
import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Evaluación repetible de calidad y costo de la búsqueda de productos (QueryParser → SearchCriteria →
 * SearchService.organic → SearchSql) sobre un PostgreSQL 17 real en Testcontainers. Sirve de compuerta a los cambios del
 * indexador, el parser y el SQL.
 *
 * <p><b>Qué ejecuta de verdad:</b> el esquema mínimo ({@code search/eval/schema.sql}: extensiones, configuración
 * {@code spanish_unaccent}, índices de {@code search_listings}), las tablas fuente ({@code catalog.sql}) y la
 * reconstrucción completa del índice con el {@link SearchIndexer} real ({@code reindexAll}); ninguna fila de
 * {@code search_listings} se inserta a mano. La búsqueda usa {@link SearchService}, {@link QueryParser} y
 * {@link SearchDictionary} reales sobre un {@link JdbcClient} del contenedor, sin caché y sin anuncios.</p>
 *
 * <p><b>Dos sustituciones, deliberadas:</b> (1) {@link ListingQueries} es un servicio JPA con muchas tablas; el
 * indexador solo le pide {@code publishedProductIds} y {@code publishedListingsOfProducts}, y la búsqueda
 * {@code publishedListingsByKeys} para hidratar la página. Se sustituyen por un doble que ejecuta la misma definición de
 * «publicado» (copiada de {@code ListingQueries.PUBLISHED_LISTINGS_WHERE_SQL}) y devuelve {@link ProductResponse}
 * mínimos. (2) Por eso la hidratación no cuenta en el costo: lo medido son las sentencias que ejecuta la búsqueda en
 * {@code search_listings} (consulta de página, umbral trigram, consultas de respaldo y facetas, más la carga del
 * vocabulario fuera de la medición); en producción la hidratación añade las consultas JPA de {@code ListingQueries}.
 * Lo que se mide de calidad es el orden de {@code product_id} de la página que devuelve {@code SearchService.search}.</p>
 *
 * <p><b>Paginación:</b> la API es base 0, así que «primera página» es {@code page=0}; {@code size=10}, relevancia, sin
 * ubicación, facetas por defecto (se piden en la página 0, como el frontend).</p>
 *
 * <p>Métricas (solo consultas con relevantes, salvo donde se indica): precisión@5 = relevantes entre los (hasta 5)
 * resultados mostrados (0 si no hay ninguno); recall@10 = relevantes en los 10 primeros / min(|relevantes|, 10); MRR = 1 /
 * posición del primer relevante (0 si no hay); «sin relevantes» = % de consultas sin ningún relevante en los 10 primeros;
 * «falsos resultados» = % de las {@code no_match} que devolvieron algo. Tiempo por consulta = mediana de 3 ejecuciones tras
 * una ronda de calentamiento; p50 y p95 sobre esas medianas.</p>
 *
 * <p>Compuerta: {@code baseline.json}. Sin archivo, se genera y pasa. Con archivo, falla si precisión@5 o MRR totales
 * bajan más de 0,01, o la media de sentencias SQL o el p95 suben más de un 20 % (el p95 además exige más de
 * {@value #TIMING_FLOOR_MS} ms de diferencia absoluta, para no fallar por ruido de milisegundos; con
 * {@code -Dsearch.eval.timingGate=false} no se compara el tiempo). {@code -Dsearch.eval.updateBaseline=true} lo reescribe.
 * Sin Docker, la clase se salta.</p>
 */
@Tag("search-eval")
@Testcontainers(disabledWithoutDocker = true)
class SearchRelevanceEvalTest {

    static final int PAGE_SIZE = 10;
    static final int WARMUP_ROUNDS = 1;
    static final int TIMED_RUNS = 3;
    static final double TIMING_FLOOR_MS = 3.0;
    static final int MIN_QUERIES = 120;
    static final int MIN_PER_KIND = 6;
    static final List<String> KINDS = List.of("exact", "natural", "typo", "vehicle_form", "reference", "manufacturer",
            "position_spec", "synonym_slang", "generic", "no_match", "long");
    static final Path BASELINE = Path.of("src", "test", "resources", "search", "eval", "baseline.json");
    static final Path REPORT_DIR = Path.of("target", "search-eval");

    /** Misma definición de «publicado» que {@code ListingQueries.PUBLISHED_LISTINGS_WHERE_SQL} (copiada). */
    static final String PUBLISHED_WHERE = """
            select i.id, i.product_id, i.branch_id, b.store_id as branch_store_id, b.name as branch_name,
                   i.price, i.stock
            from inventory i
            join branches b on b.id = i.branch_id and b.deleted_at is null
            join store s on s.id = b.store_id and s.deleted_at is null
            join states ss on ss.id = s.state and ss.deleted_at is null
            join states bs on bs.id = b.state and bs.deleted_at is null
            join products p on p.id = i.product_id and p.deleted_at is null
            join manufacturers m on m.id = p.manufacturer_id and m.deleted_at is null
            join categories c on c.id = p.category_id and c.deleted_at is null
            join states ps on ps.id = p.state_id and ps.deleted_at is null
            where i.deleted_at is null
              and ps.code = 'ACTIVE'
              and ss.code = 'ACTIVE'
              and bs.code = 'ACTIVE'
              and i.stock > 0
            """;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static final AtomicLong STATEMENTS = new AtomicLong();
    static DataSource admin;
    static SearchService service;
    static TransactionTemplate readOnly;
    static Map<UUID, String> names = new HashMap<>();
    static Set<UUID> indexedProducts;
    static int indexedListings;

    @BeforeAll
    static void prepare() throws Exception {
        admin = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        runScript("search/eval/schema.sql");
        runScript("search/eval/catalog.sql");
        JdbcTemplate jdbc = new JdbcTemplate(admin);
        jdbc.query("select id, name from products", rs -> {
            names.put(rs.getObject("id", UUID.class), rs.getString("name"));
        });

        // Doble de ListingQueries: solo lo que usan el indexador y la hidratación (ver Javadoc de la clase).
        ListingQueries listingQueries = mock(ListingQueries.class);
        when(listingQueries.publishedProductIds()).thenAnswer(call -> jdbc.queryForList(
                "select distinct x.product_id from (" + PUBLISHED_WHERE + ") x order by x.product_id", UUID.class));
        when(listingQueries.publishedListingsOfProducts(anyCollection())).thenAnswer(call -> {
            Collection<UUID> ids = call.getArgument(0);
            String marks = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
            return jdbc.query(PUBLISHED_WHERE + " and p.id in (" + marks + ") order by p.id, b.id",
                    (rs, row) -> listing(rs.getObject("product_id", UUID.class), rs.getObject("branch_id", UUID.class),
                            rs.getObject("branch_store_id", UUID.class), rs.getString("branch_name"),
                            rs.getBigDecimal("price"), rs.getInt("stock")),
                    ids.toArray());
        });
        when(listingQueries.publishedListingsByKeys(anyList(), any())).thenAnswer(call -> {
            List<ListingKey> keys = call.getArgument(0);
            List<ProductResponse> out = new ArrayList<>();
            for (ListingKey key : keys) {
                out.add(listing(key.productId(), key.branchId(), null, null, BigDecimal.ZERO, 1));
            }
            return out;
        });

        // Indexación real: reconstrucción completa con el SearchIndexer de producción.
        SearchIndexer indexer = new SearchIndexer(JdbcClient.create(admin), listingQueries,
                new DataSourceTransactionManager(admin));
        int reindexed = indexer.reindexAll();
        indexedListings = jdbc.queryForObject("select count(*) from search_listings", Integer.class);
        int published = jdbc.queryForObject("select count(*) from (" + PUBLISHED_WHERE + ") x", Integer.class);
        assertThat(indexedListings).as("search_listings tras reindexAll").isEqualTo(published).isEqualTo(reindexed)
                .isPositive();
        indexedProducts = new LinkedHashSet<>(jdbc.queryForList("select distinct product_id from search_listings",
                UUID.class));
        jdbc.execute("analyze");

        // Búsqueda real sobre una conexión propia con conteo de sentencias; sin caché ni anuncios.
        SingleConnectionDataSource shared = new SingleConnectionDataSource(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword(), true);
        DataSource counting = counting(shared, STATEMENTS);
        JdbcClient client = JdbcClient.create(counting);
        service = new SearchService(client, new QueryParser(new SearchDictionary(client)), listingQueries,
                (com.autopartco.backend.core.advertising.AdServer) null);
        readOnly = new TransactionTemplate(new DataSourceTransactionManager(counting));
        readOnly.setReadOnly(true);
    }

    @Test
    void evaluate() throws Exception {
        List<Golden> golden = loadGolden();
        validateGolden(golden);

        // Calentamiento: JIT, plan cache de PostgreSQL y vocabulario del parser.
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            for (Golden query : golden) {
                run(query.query());
            }
        }

        List<Outcome> outcomes = new ArrayList<>();
        for (Golden query : golden) {
            List<Double> times = new ArrayList<>();
            Run first = null;
            long statements = 0;
            for (int i = 0; i < TIMED_RUNS; i++) {
                Run run = run(query.query());
                times.add(run.millis());
                statements = run.statements();
                if (first == null) {
                    first = run;
                }
            }
            times.sort(Double::compare);
            outcomes.add(outcome(query, first.ids(), statements, times.get(times.size() / 2)));
        }

        Metrics total = metrics(outcomes);
        Map<String, Metrics> byKind = new LinkedHashMap<>();
        for (String kind : KINDS) {
            byKind.put(kind, metrics(outcomes.stream().filter(o -> o.golden().kind().equals(kind)).toList()));
        }

        printTable(total, byKind);
        writeReports(outcomes, total, byKind);
        gate(total, byKind);
    }

    // ------------------------------------------------------------------ ejecución

    record Run(List<UUID> ids, long statements, double millis) {
    }

    private static Run run(String text) {
        STATEMENTS.set(0);
        long start = System.nanoTime();
        SearchResult result = readOnly.execute(status -> service.search(
                SearchQuery.builder().q(text).page(0).size(PAGE_SIZE).sort(SearchSort.RELEVANCE).build(), null));
        double millis = (System.nanoTime() - start) / 1_000_000.0;
        long statements = STATEMENTS.get();
        List<UUID> ids = result.items().stream().map(hit -> hit.listing().id()).toList();
        return new Run(ids, statements, millis);
    }

    private static ProductResponse listing(UUID productId, UUID branchId, UUID storeId, String branchName,
            BigDecimal price, int stock) {
        return new ProductResponse(productId, storeId, branchId, branchName, names.get(productId), null, null, null,
                price, price, null, null, stock, null, null, true, null, "ACTIVE", false, Map.of(), List.of(),
                List.of(), null, null, null, 0);
    }

    // ------------------------------------------------------------------ conteo de sentencias

    /** DataSource que cuenta cada {@code execute*} de Statement/PreparedStatement/CallableStatement. */
    static DataSource counting(DataSource target, AtomicLong counter) {
        InvocationHandler handler = (proxy, method, args) -> {
            Object result = invoke(target, method, args);
            if (method.getName().equals("getConnection") && result instanceof Connection connection) {
                return wrapConnection(connection, counter);
            }
            return result;
        };
        return (DataSource) Proxy.newProxyInstance(SearchRelevanceEvalTest.class.getClassLoader(),
                new Class<?>[] {DataSource.class}, handler);
    }

    private static Connection wrapConnection(Connection connection, AtomicLong counter) {
        InvocationHandler handler = (proxy, method, args) -> {
            Object result = invoke(connection, method, args);
            String name = method.getName();
            if ((name.equals("prepareStatement") || name.equals("createStatement") || name.equals("prepareCall"))
                    && result instanceof Statement statement) {
                return Proxy.newProxyInstance(SearchRelevanceEvalTest.class.getClassLoader(),
                        new Class<?>[] {method.getReturnType()}, (p, m, a) -> {
                            if (m.getName().startsWith("execute")) {
                                counter.incrementAndGet();
                            }
                            return invoke(statement, m, a);
                        });
            }
            return result;
        };
        return (Connection) Proxy.newProxyInstance(SearchRelevanceEvalTest.class.getClassLoader(),
                new Class<?>[] {Connection.class}, handler);
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    private static void runScript(String resource) throws Exception {
        try (Connection connection = admin.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource(resource),
                    StandardCharsets.UTF_8));
        }
    }

    // ------------------------------------------------------------------ consultas doradas

    record Golden(String id, String query, Set<UUID> expected, String kind, String notes) {
    }

    private static List<Golden> loadGolden() throws IOException {
        ClassPathResource resource = new ClassPathResource("search/eval/golden-queries.csv");
        List<String> lines = new ArrayList<>(Files.readAllLines(Path.of(resource.getURI()), StandardCharsets.UTF_8));
        List<Golden> out = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            List<String> cells = csv(line);
            assertThat(cells).as("columnas de: " + line).hasSize(5);
            Set<UUID> expected = new LinkedHashSet<>();
            if (!cells.get(2).isBlank()) {
                for (String id : cells.get(2).split("\\|")) {
                    expected.add(UUID.fromString(id.strip()));
                }
            }
            out.add(new Golden(cells.get(0), cells.get(1), expected, cells.get(3), cells.get(4)));
        }
        return out;
    }

    /** Una línea de CSV con comillas dobles y {@code ""} como comilla escapada. */
    static List<String> csv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }

    private static void validateGolden(List<Golden> golden) {
        assertThat(golden).as("consultas doradas").hasSizeGreaterThanOrEqualTo(MIN_QUERIES);
        Map<String, Long> perKind = new TreeMap<>();
        golden.forEach(g -> perKind.merge(g.kind(), 1L, Long::sum));
        assertThat(perKind.keySet()).as("clases de consulta").containsExactlyInAnyOrderElementsOf(KINDS);
        perKind.forEach((kind, count) -> assertThat(count).as("consultas de la clase " + kind)
                .isGreaterThanOrEqualTo(MIN_PER_KIND));
        assertThat(golden.stream().map(Golden::id).distinct().count()).as("ids únicos").isEqualTo(golden.size());
        for (Golden g : golden) {
            for (UUID id : g.expected()) {
                assertThat(names).as("%s: expected_product_id %s debe existir en el fixture", g.id(), id)
                        .containsKey(id);
                assertThat(indexedProducts).as("%s: %s debe quedar indexado por el indexador", g.id(), id)
                        .contains(id);
            }
            if (g.kind().equals("no_match")) {
                assertThat(g.expected()).as("%s: no_match sin relevantes", g.id()).isEmpty();
            } else {
                assertThat(g.expected()).as("%s: consulta con relevantes", g.id()).isNotEmpty();
            }
        }
    }

    // ------------------------------------------------------------------ métricas

    record Outcome(Golden golden, List<UUID> returned, long statements, double millis, double precisionAt5,
            double recallAt10, double reciprocalRank, boolean noRelevant, boolean falseResult) {
    }

    private static Outcome outcome(Golden golden, List<UUID> returned, long statements, double millis) {
        Set<UUID> expected = golden.expected();
        List<UUID> top5 = returned.subList(0, Math.min(5, returned.size()));
        List<UUID> top10 = returned.subList(0, Math.min(10, returned.size()));
        long relevantTop5 = top5.stream().filter(expected::contains).count();
        long relevantTop10 = top10.stream().filter(expected::contains).count();
        double precision = top5.isEmpty() ? 0 : (double) relevantTop5 / top5.size();
        double recall = expected.isEmpty() ? 0 : (double) relevantTop10 / Math.min(expected.size(), 10);
        double rr = 0;
        for (int i = 0; i < top10.size(); i++) {
            if (expected.contains(top10.get(i))) {
                rr = 1.0 / (i + 1);
                break;
            }
        }
        return new Outcome(golden, returned, statements, millis, precision, recall, rr,
                !expected.isEmpty() && relevantTop10 == 0, expected.isEmpty() && !returned.isEmpty());
    }

    record Metrics(int queries, int withRelevant, int noMatch, Double precisionAt5, Double recallAt10, Double mrr,
            Double noRelevantPct, Double falseResultsPct, double avgSql, double p50Ms, double p95Ms) {
    }

    private static Metrics metrics(List<Outcome> outcomes) {
        List<Outcome> relevant = outcomes.stream().filter(o -> !o.golden().expected().isEmpty()).toList();
        List<Outcome> none = outcomes.stream().filter(o -> o.golden().expected().isEmpty()).toList();
        List<Double> times = outcomes.stream().map(Outcome::millis).sorted().toList();
        return new Metrics(outcomes.size(), relevant.size(), none.size(),
                mean(relevant, Outcome::precisionAt5), mean(relevant, Outcome::recallAt10),
                mean(relevant, Outcome::reciprocalRank),
                relevant.isEmpty() ? null : round(100.0 * relevant.stream().filter(Outcome::noRelevant).count()
                        / relevant.size()),
                none.isEmpty() ? null : round(100.0 * none.stream().filter(Outcome::falseResult).count()
                        / none.size()),
                round(outcomes.stream().mapToLong(Outcome::statements).average().orElse(0)),
                round(percentile(times, 0.50)), round(percentile(times, 0.95)));
    }

    private static Double mean(List<Outcome> outcomes, java.util.function.ToDoubleFunction<Outcome> value) {
        return outcomes.isEmpty() ? null : round(outcomes.stream().mapToDouble(value).average().orElse(0));
    }

    private static double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    private static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    // ------------------------------------------------------------------ informe

    private static String cell(Double value, String format) {
        return value == null ? "-" : String.format(Locale.ROOT, format, value);
    }

    private static String metricsRow(String label, Metrics m) {
        return String.format(Locale.ROOT, "| %-14s | %4d | %5s | %5s | %5s | %7s | %8s | %7.2f | %6.1f | %6.1f |",
                label, m.queries(), cell(m.precisionAt5(), "%.3f"), cell(m.recallAt10(), "%.3f"),
                cell(m.mrr(), "%.3f"), cell(m.noRelevantPct(), "%.1f"), cell(m.falseResultsPct(), "%.1f"),
                m.avgSql(), m.p50Ms(), m.p95Ms());
    }

    private static final String HEADER = "| clase          |    n |  P@5  | R@10  |  MRR  | sin rel% | falsos% | SQL med | p50 ms | p95 ms |";
    private static final String RULE = "|----------------|------|-------|-------|-------|---------|----------|---------|--------|--------|";

    private static List<String> tableLines(Metrics total, Map<String, Metrics> byKind) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.add(RULE);
        lines.add(metricsRow("TOTAL", total));
        byKind.forEach((kind, m) -> lines.add(metricsRow(kind, m)));
        return lines;
    }

    private static void printTable(Metrics total, Map<String, Metrics> byKind) {
        System.out.println();
        System.out.println("== Evaluación de búsqueda (" + indexedListings + " listados indexados) ==");
        tableLines(total, byKind).forEach(System.out::println);
        System.out.println();
    }

    private static List<Outcome> worst(List<Outcome> outcomes, int count) {
        return outcomes.stream().filter(o -> !o.golden().expected().isEmpty())
                .sorted(Comparator.comparingDouble((Outcome o) -> o.reciprocalRank() + o.precisionAt5())
                        .thenComparing(o -> o.golden().id()))
                .limit(count).toList();
    }

    private static String nameOf(UUID id) {
        return names.getOrDefault(id, id.toString());
    }

    private static void writeReports(List<Outcome> outcomes, Metrics total, Map<String, Metrics> byKind)
            throws IOException {
        Files.createDirectories(REPORT_DIR);

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("listings", indexedListings);
        json.put("pageSize", PAGE_SIZE);
        json.put("total", toMap(total));
        Map<String, Object> kinds = new LinkedHashMap<>();
        byKind.forEach((kind, m) -> kinds.put(kind, toMap(m)));
        json.put("byKind", kinds);
        List<Object> queries = new ArrayList<>();
        for (Outcome o : outcomes) {
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("id", o.golden().id());
            q.put("kind", o.golden().kind());
            q.put("query", o.golden().query());
            q.put("expected", o.golden().expected().size());
            q.put("returned", o.returned().size());
            q.put("precisionAt5", round(o.precisionAt5()));
            q.put("recallAt10", round(o.recallAt10()));
            q.put("reciprocalRank", round(o.reciprocalRank()));
            q.put("sqlStatements", o.statements());
            q.put("millis", round(o.millis()));
            List<Object> top = new ArrayList<>();
            for (UUID id : o.returned().subList(0, Math.min(5, o.returned().size()))) {
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("name", nameOf(id));
                hit.put("relevant", o.golden().expected().contains(id));
                top.add(hit);
            }
            q.put("top5", top);
            queries.add(q);
        }
        json.put("queries", queries);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(REPORT_DIR.resolve("report.json").toFile(),
                json);

        StringBuilder md = new StringBuilder();
        md.append("# Evaluación de búsqueda\n\n").append(indexedListings).append(" listados indexados; ")
                .append(outcomes.size()).append(" consultas; página de ").append(PAGE_SIZE).append(".\n\n");
        md.append("P@5 = relevantes entre los (hasta 5) mostrados; R@10 = relevantes en 10 / min(|relevantes|, 10); ")
                .append("sin rel% = consultas sin ningún relevante en 10; falsos% = `no_match` que devolvieron algo; ")
                .append("SQL med = sentencias por búsqueda; p50/p95 = mediana de 3 ejecuciones por consulta.\n\n");
        tableLines(total, byKind).forEach(line -> md.append(line).append('\n'));
        md.append("\n## Peores 10 casos\n\n");
        for (Outcome o : worst(outcomes, 10)) {
            md.append("- **").append(o.golden().id()).append("** (").append(o.golden().kind()).append(") `")
                    .append(o.golden().query()).append("` — P@5 ").append(String.format(Locale.ROOT, "%.2f",
                            o.precisionAt5())).append(", RR ").append(String.format(Locale.ROOT, "%.2f",
                            o.reciprocalRank())).append('\n');
            md.append("  - esperaba (").append(o.golden().expected().size()).append("): ")
                    .append(o.golden().expected().stream().limit(3).map(SearchRelevanceEvalTest::nameOf)
                            .reduce((a, b) -> a + " ; " + b).orElse("")).append('\n');
            md.append("  - devolvió: ").append(o.returned().isEmpty() ? "nada"
                    : o.returned().stream().limit(3).map(id -> nameOf(id) + (o.golden().expected().contains(id)
                            ? " [ok]" : " [x]")).reduce((a, b) -> a + " ; " + b).orElse("")).append('\n');
        }
        md.append("\n## Detalle por consulta\n\n");
        md.append("| id | clase | consulta | sql | ms | P@5 | RR | 5 primeros (✓ relevante, ✗ no) |\n");
        md.append("|---|---|---|---|---|---|---|---|\n");
        for (Outcome o : outcomes) {
            String top = o.returned().isEmpty() ? "(sin resultados)" : o.returned().stream().limit(5)
                    .map(id -> (o.golden().expected().contains(id) ? "✓ " : "✗ ") + nameOf(id).replace("|", "/"))
                    .reduce((a, b) -> a + "<br>" + b).orElse("");
            md.append(String.format(Locale.ROOT, "| %s | %s | %s | %d | %.1f | %.2f | %.2f | %s |%n", o.golden().id(),
                    o.golden().kind(), o.golden().query().replace("|", "/"), o.statements(), o.millis(),
                    o.precisionAt5(), o.reciprocalRank(), top));
        }
        Files.writeString(REPORT_DIR.resolve("report.md"), md.toString(), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> toMap(Metrics m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("queries", m.queries());
        map.put("withRelevant", m.withRelevant());
        map.put("noMatch", m.noMatch());
        map.put("precisionAt5", m.precisionAt5());
        map.put("recallAt10", m.recallAt10());
        map.put("mrr", m.mrr());
        map.put("noRelevantPct", m.noRelevantPct());
        map.put("falseResultsPct", m.falseResultsPct());
        map.put("avgSql", m.avgSql());
        map.put("p50Ms", m.p50Ms());
        map.put("p95Ms", m.p95Ms());
        return map;
    }

    // ------------------------------------------------------------------ compuerta

    private static void gate(Metrics total, Map<String, Metrics> byKind) throws IOException {
        boolean update = Boolean.getBoolean("search.eval.updateBaseline");
        if (update || !Files.exists(BASELINE)) {
            Map<String, Object> baseline = new LinkedHashMap<>();
            baseline.put("description", "Línea base de SearchRelevanceEvalTest. Regenerar con "
                    + "-Dsearch.eval.updateBaseline=true solo tras una mejora aceptada.");
            baseline.put("total", toMap(total));
            Map<String, Object> kinds = new LinkedHashMap<>();
            byKind.forEach((kind, m) -> kinds.put(kind, toMap(m)));
            baseline.put("byKind", kinds);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(BASELINE.toFile(), baseline);
            System.out.println("Línea base " + (update ? "actualizada" : "generada") + ": " + BASELINE);
            return;
        }
        JsonNode base = new ObjectMapper().readTree(BASELINE.toFile()).get("total");
        List<String> failures = new ArrayList<>();
        double basePrecision = base.get("precisionAt5").asDouble();
        double baseMrr = base.get("mrr").asDouble();
        double baseSql = base.get("avgSql").asDouble();
        double baseP95 = base.get("p95Ms").asDouble();
        if (total.precisionAt5() < basePrecision - 0.01) {
            failures.add("precisión@5 " + total.precisionAt5() + " < línea base " + basePrecision + " - 0,01");
        }
        if (total.mrr() < baseMrr - 0.01) {
            failures.add("MRR " + total.mrr() + " < línea base " + baseMrr + " - 0,01");
        }
        if (total.avgSql() > baseSql * 1.2) {
            failures.add("sentencias SQL medias " + total.avgSql() + " > línea base " + baseSql + " + 20 %");
        }
        boolean timing = !"false".equalsIgnoreCase(System.getProperty("search.eval.timingGate"));
        if (timing && total.p95Ms() > baseP95 * 1.2 && total.p95Ms() - baseP95 > TIMING_FLOOR_MS) {
            failures.add("p95 " + total.p95Ms() + " ms > línea base " + baseP95 + " ms + 20 %");
        }
        assertThat(failures).as("La búsqueda empeoró frente a baseline.json").isEmpty();
    }
}
