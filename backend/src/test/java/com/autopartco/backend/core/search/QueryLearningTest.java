package com.autopartco.backend.core.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.autopartco.backend.core.catalog.ListingKey;
import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.catalog.ProductResponse;

/**
 * Aprendizaje de los clics sobre un PostgreSQL 17 real (Testcontainers, esquema de {@code search/eval/schema.sql}): el
 * job nocturno recalcula {@code search_query_category}, {@link QueryLearning} elige la categoría ganadora (3 clics o
 * más y 50 % o más de los clics de la consulta) y esa categoría ordena antes que otro producto igual de relevante. Sin
 * Docker, la clase se salta.
 */
@Testcontainers(disabledWithoutDocker = true)
class QueryLearningTest {
    static final UUID STATE = UUID.randomUUID();
    static final UUID STORE = UUID.randomUUID();
    static final UUID BRANCH = UUID.randomUUID();
    static final UUID CAT_A = UUID.randomUUID();
    static final UUID CAT_B = UUID.randomUUID();
    static final UUID CAT_C = UUID.randomUUID();
    // Ids ordenados: sin aprendizaje, el desempate final por product_id deja primero al de la categoría A.
    static final UUID PRODUCT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID PRODUCT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    static final UUID PRODUCT_C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static DataSource dataSource;
    static JdbcTemplate jdbc;
    static QueryLearning learning;
    static QueryLearningJob job;
    static SearchService service;

    @BeforeAll
    static void prepare() throws Exception {
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new ClassPathResource("search/eval/schema.sql"), StandardCharsets.UTF_8));
        }
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("insert into states (id, code) values (?, 'ACTIVE')", STATE);
        jdbc.update("insert into store (id, business_name, state) values (?, 'Tienda', ?)", STORE, STATE);
        jdbc.update("insert into categories (id, code) values (?, 'A'), (?, 'B'), (?, 'C')", CAT_A, CAT_B, CAT_C);
        product(PRODUCT_A, CAT_A);
        product(PRODUCT_B, CAT_B);
        product(PRODUCT_C, CAT_C);
        listing(PRODUCT_A, CAT_A);
        listing(PRODUCT_B, CAT_B);
        jdbc.execute("analyze search_listings");

        JdbcClient client = JdbcClient.create(dataSource);
        learning = new QueryLearning(client);
        job = new QueryLearningJob(client, new DataSourceTransactionManager(dataSource), learning);

        SearchDictionary dictionary = mock(SearchDictionary.class);
        when(dictionary.vocabulary()).thenReturn(new SearchDictionary.Vocabulary(List.of(), List.of(), List.of(),
                Map.of(), Map.of("bujia", 10, "iridium", 10)));
        ListingQueries listingQueries = mock(ListingQueries.class);
        when(listingQueries.publishedListingsByKeys(anyList(), any())).thenAnswer(call -> {
            List<ListingKey> keys = call.getArgument(0);
            List<ProductResponse> out = new ArrayList<>();
            for (ListingKey key : keys) {
                out.add(new ProductResponse(key.productId(), STORE, key.branchId(), "Sede", "Bujia iridium", null,
                        null, null, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, null, 1, null, null, false, null,
                        "ACTIVE", false, Map.of(), List.of(), List.of()));
            }
            return out;
        });
        service = new SearchService(client, new QueryParser(dictionary), listingQueries);
        service.setQueryLearning(learning);
    }

    @BeforeEach
    void clean() {
        jdbc.execute("truncate search_clicks, marketplace_searches, search_query_category");
        learning.reload();
    }

    @Test
    void jobRecomputesCountsFromScratchWithTheLastNinetyDays() {
        jdbc.update("insert into search_query_category (normalized_query, category_id, clicks, impressions) "
                + "values ('viejo', ?, 9, 9)", CAT_A);
        // «bujia»: 5 búsquedas con resultados (+1 sin resultados y +1 de hace 100 días, que no cuentan).
        List<UUID> searches = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            searches.add(search("bujia", 3, "now()"));
        }
        UUID empty = search("bujia", 0, "now()");
        UUID old = search("bujia", 3, "now() - interval '100 days'");
        for (int i = 0; i < 4; i++) {
            click(searches.get(i), PRODUCT_B, "now()");
        }
        click(searches.get(4), PRODUCT_A, "now()");
        click(empty, PRODUCT_A, "now()");
        click(old, PRODUCT_A, "now() - interval '100 days'");

        job.learn();

        assertThat(rows()).containsOnlyKeys("bujia|" + CAT_A, "bujia|" + CAT_B)
                .containsEntry("bujia|" + CAT_A, List.of(2, 5))
                .containsEntry("bujia|" + CAT_B, List.of(4, 5));
        // Segunda ejecución: mismo resultado, sin duplicar.
        job.learn();
        assertThat(rows()).containsEntry("bujia|" + CAT_B, List.of(4, 5)).hasSize(2);
    }

    @Test
    void winnerNeedsThreeClicksAndHalfOfTheClicksOfTheQuery() {
        learn("pocos", Map.of(PRODUCT_A, 2));
        learn("justo", Map.of(PRODUCT_A, 3));
        learn("mayoria", Map.of(PRODUCT_A, 6, PRODUCT_B, 4));
        learn("mitad", Map.of(PRODUCT_A, 4, PRODUCT_B, 2, PRODUCT_C, 2));
        learn("repartido", Map.of(PRODUCT_A, 3, PRODUCT_B, 2, PRODUCT_C, 2));
        learn("empate", Map.of(PRODUCT_A, 3, PRODUCT_B, 3));

        job.learn();

        assertThat(learning.categoryFor("pocos")).isEmpty();
        assertThat(learning.categoryFor("justo")).contains(CAT_A);
        assertThat(learning.categoryFor("mayoria")).contains(CAT_A);
        assertThat(learning.categoryFor("mitad")).contains(CAT_A);
        assertThat(learning.categoryFor("repartido")).isEmpty();
        assertThat(learning.categoryFor("empate")).isEmpty();
        assertThat(learning.categoryFor("desconocida")).isEmpty();
        assertThat(learning.categoryFor(null)).isEmpty();
        // La clave se normaliza igual que marketplace_searches.query_normalized.
        assertThat(learning.categoryFor("  JUSTÓ  ")).contains(CAT_A);
    }

    @Test
    void learnedCategoryPutsItsProductFirstAmongEquallyRelevantOnes() {
        assertThat(order("bujia iridium")).containsExactly(PRODUCT_A, PRODUCT_B);

        jdbc.update("insert into search_query_category (normalized_query, category_id, clicks, impressions) "
                + "values ('bujia iridium', ?, 5, 8)", CAT_B);
        learning.reload();

        assertThat(learning.categoryFor("bujia iridium")).contains(CAT_B);
        // No filtra: el otro producto sigue en el resultado, detrás.
        assertThat(order("bujia iridium")).containsExactly(PRODUCT_B, PRODUCT_A);
        // Otra consulta no se ve afectada.
        assertThat(order("bujia")).containsExactly(PRODUCT_A, PRODUCT_B);
    }

    private static List<UUID> order(String q) {
        return service.search(SearchQuery.builder().q(q).facets(false).page(0).size(24).build(), null).items()
                .stream().map(hit -> hit.listing().id()).toList();
    }

    /** Una búsqueda con resultados por cada clic y los clics en los productos indicados ({@code producto → clics}). */
    private static void learn(String query, Map<UUID, Integer> clicks) {
        clicks.forEach((product, count) -> {
            for (int i = 0; i < count; i++) {
                click(search(query, 4, "now()"), product, "now()");
            }
        });
    }

    private static UUID search(String query, int results, String occurredAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into marketplace_searches (id, occurred_at, query_text, query_normalized, results_count, "
                + "authenticated) values (?, " + occurredAt + ", ?, ?, ?, false)", id, query, query, results);
        return id;
    }

    private static void click(UUID search, UUID product, String createdAt) {
        jdbc.update("insert into search_clicks (search_id, product_id, position, created_at) values (?, ?, 0, "
                + createdAt + ")", search, product);
    }

    /** {@code consulta|categoría → [clics, impresiones]}. */
    private static Map<String, List<Integer>> rows() {
        Map<String, List<Integer>> rows = new HashMap<>();
        jdbc.query("select normalized_query, category_id, clicks, impressions from search_query_category", rs -> {
            rows.put(rs.getString("normalized_query") + "|" + rs.getObject("category_id", UUID.class),
                    List.of(rs.getInt("clicks"), rs.getInt("impressions")));
        });
        return rows;
    }

    private static void product(UUID id, UUID category) {
        jdbc.update("insert into products (id, store_id, name, state_id, category_id) values (?, ?, 'Bujia iridium', "
                + "?, ?)", id, STORE, STATE, category);
    }

    private static void listing(UUID product, UUID category) {
        jdbc.update("""
                insert into search_listings (listing_key, product_id, branch_id, store_id, category_id, category_path,
                    name, price, stock, has_promotion, universal, vehicle_make_ids, vehicle_model_ids,
                    vehicle_version_ids, document, search_text, published_at, updated_at)
                values (?, ?, ?, ?, ?, array[?::uuid], 'Bujia iridium', 10000, 5, false, true, '{}', '{}', '{}',
                    setweight(to_tsvector('public.spanish_unaccent'::regconfig, 'Bujia iridium'), 'A'),
                    'bujia iridium', now(), now())""",
                product + ":" + BRANCH, product, BRANCH, STORE, category, category);
    }
}
