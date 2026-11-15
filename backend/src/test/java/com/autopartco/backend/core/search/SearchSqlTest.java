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
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.autopartco.backend.core.catalog.ListingKey;
import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.search.SearchDictionary.Make;
import com.autopartco.backend.core.search.SearchDictionary.Model;

/**
 * Reglas de la consulta única de {@link SearchSql} sobre un PostgreSQL 17 real (Testcontainers, esquema de
 * {@code search/eval/schema.sql}): exclusión del vehículo interpretado, {@code relaxed}, referencia exacta, cobertura y
 * facetas. Las filas de {@code search_listings} se insertan a mano con el mismo {@code document} ponderado que arma el
 * indexador (nombre y referencia en A, descripción en D). Sin Docker, la clase se salta.
 */
@Testcontainers(disabledWithoutDocker = true)
class SearchSqlTest {
    static final Make MAZDA = new Make(UUID.randomUUID(), "Mazda");
    static final Make KIA = new Make(UUID.randomUUID(), "Kia");
    static final Model MAZDA_3 = new Model(UUID.randomUUID(), MAZDA.id(), "3");
    static final Model RIO = new Model(UUID.randomUUID(), KIA.id(), "Rio");
    static final UUID FRENOS = UUID.randomUUID();
    static final UUID MOTOR = UUID.randomUUID();
    static final UUID STORE = UUID.randomUUID();
    static final UUID BRANCH = UUID.randomUUID();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static final Map<String, UUID> PRODUCTS = new HashMap<>();
    static final Map<UUID, String> NAMES = new HashMap<>();
    static SearchService service;

    @BeforeAll
    static void prepare() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new ClassPathResource("search/eval/schema.sql"), StandardCharsets.UTF_8));
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("insert into categories (id, code) values (?, 'FRENOS'), (?, 'MOTOR')", FRENOS, MOTOR);

        // Vehículo interpretado: compatible por datos, de otra marca, universal y sin datos ni mención.
        listing(jdbc, "mazda", "Pastillas freno Mazda 3", null, null, FRENOS, false, MAZDA, MAZDA_3, MAZDA, MAZDA_3, 5);
        listing(jdbc, "kia", "Pastillas freno Kia Rio", null, null, FRENOS, false, KIA, RIO, KIA, RIO, 5);
        listing(jdbc, "universal", "Pastillas freno universales", null, null, FRENOS, true, null, null, null, null,
                0);
        listing(jdbc, "ceramica", "Pastillas freno ceramica", null, null, FRENOS, false, null, null, null, null, 2);
        // Sin compatibles con Mazda: solo otro vehículo y un universal.
        listing(jdbc, "amortiguador-kia", "Amortiguador Kia Rio", null, null, MOTOR, false, KIA, RIO, KIA, RIO, 3);
        listing(jdbc, "amortiguador-universal", "Amortiguador universal", null, null, MOTOR, true, null, null, null,
                null, 3);
        // Referencia: exacta en el peso A frente a la misma referencia solo en la descripción (peso D).
        listing(jdbc, "ref-exacta", "Bomba agua", "81928-65477", null, MOTOR, false, null, null, null, null, 1);
        listing(jdbc, "ref-descripcion", "Bomba agua reforzada", null, "Reemplaza la 81928-65477", MOTOR, false, null,
                null, null, null, 9);
        listing(jdbc, "ref-sin", "Bomba agua alta presion", null, null, MOTOR, false, null, null, null, null, 9);
        // Cobertura.
        listing(jdbc, "filtro-3", "Filtro aceite motor", null, null, MOTOR, false, null, null, null, null, 4);
        listing(jdbc, "filtro-2", "Filtro aceite", null, null, MOTOR, false, null, null, null, null, 4);
        listing(jdbc, "filtro-diesel", "Filtro aceite motor diesel", null, null, MOTOR, false, null, null, null,
                null, 0);
        jdbc.execute("analyze search_listings");

        Map<String, Integer> lexicon = new HashMap<>();
        for (String word : List.of("pastillas", "freno", "universales", "ceramica", "amortiguador", "universal",
                "bomba", "agua", "reforzada", "alta", "presion", "reemplaza", "filtro", "aceite", "motor", "diesel",
                "sintetico")) {
            lexicon.put(word, 10);
        }
        SearchDictionary dictionary = mock(SearchDictionary.class);
        when(dictionary.vocabulary()).thenReturn(new SearchDictionary.Vocabulary(List.of(MAZDA, KIA),
                List.of(MAZDA_3, RIO), List.of(), Map.of(), lexicon));
        ListingQueries listingQueries = mock(ListingQueries.class);
        when(listingQueries.publishedListingsByKeys(anyList(), any())).thenAnswer(call -> {
            List<ListingKey> keys = call.getArgument(0);
            List<ProductResponse> out = new ArrayList<>();
            for (ListingKey key : keys) {
                out.add(new ProductResponse(key.productId(), STORE, key.branchId(), "Sede", NAMES.get(key.productId()),
                        null, null, null, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, null, 1, null, null, false,
                        null, "ACTIVE", false, Map.of(), List.of(), List.of()));
            }
            return out;
        });
        service = new SearchService(JdbcClient.create(dataSource), new QueryParser(dictionary), listingQueries);
    }

    private static void listing(JdbcTemplate jdbc, String key, String name, String reference, String description,
            UUID category, boolean universal, Make make, Model model, Make mentionedMake, Model mentionedModel,
            int stock) {
        UUID product = UUID.randomUUID();
        PRODUCTS.put(key, product);
        NAMES.put(product, name);
        jdbc.update("""
                insert into search_listings (listing_key, product_id, branch_id, store_id, category_id, category_path,
                    name, price, stock, has_promotion, universal, vehicle_make_ids, vehicle_model_ids,
                    vehicle_version_ids, document, search_text, published_at, updated_at, mentioned_make_ids,
                    mentioned_model_ids)
                values (?, ?, ?, ?, ?, array[?::uuid], ?, 10000, ?, false, ?, ?::uuid[], ?::uuid[], '{}'::uuid[],
                    setweight(to_tsvector('public.spanish_unaccent'::regconfig, concat_ws(' ', ?::text, ?::text)), 'A')
                    || setweight(to_tsvector('public.spanish_unaccent'::regconfig, coalesce(?::text, '')), 'D'),
                    lower(?), now(), now(), ?::uuid[], ?::uuid[])""",
                product + ":" + BRANCH, product, BRANCH, STORE, category, category, name, stock, universal,
                array(make == null ? null : make.id()), array(model == null ? null : model.id()), name, reference,
                description, name, array(mentionedMake == null ? null : mentionedMake.id()),
                array(mentionedModel == null ? null : mentionedModel.id()));
    }

    private static String array(UUID id) {
        return id == null ? "{}" : "{" + id + "}";
    }

    private static SearchResult search(String q) {
        return service.search(SearchQuery.builder().q(q).page(0).size(24).sort(SearchSort.RELEVANCE).build(), null);
    }

    private static List<String> keys(SearchResult result) {
        Map<UUID, String> byId = new HashMap<>();
        PRODUCTS.forEach((key, id) -> byId.put(id, key));
        return result.items().stream().map(hit -> byId.get(hit.listing().id())).toList();
    }

    @Test
    void anotherMakesProductIsLeftOutWhenTheMakeIsInterpreted() {
        SearchResult result = search("pastillas freno mazda");

        assertThat(result.interpreted().makeId()).isEqualTo(MAZDA.id());
        assertThat(keys(result)).doesNotContain("kia").startsWith("mazda")
                .containsExactlyInAnyOrder("mazda", "universal", "ceramica");
        assertThat(result.interpreted().relaxed()).isEqualTo(SearchInterpretation.Relaxed.NONE);
        assertThat(result.fuzzy()).isFalse();
    }

    @Test
    void withNoCompatibleProductsTheUniversalShowsUpRelaxed() {
        SearchResult result = search("amortiguador mazda");

        assertThat(keys(result)).containsExactly("amortiguador-universal");
        assertThat(result.interpreted().relaxed()).isEqualTo(SearchInterpretation.Relaxed.VEHICLE);
    }

    @Test
    void anExactReferenceComesFirst() {
        SearchResult result = search("bomba 81928-65477");

        assertThat(result.interpreted().reference()).isEqualTo("81928-65477");
        // «bomba» + referencia = 2 unidades: la que solo dice «bomba agua» no cubre la referencia y queda fuera.
        assertThat(keys(result)).containsExactly("ref-exacta", "ref-descripcion");
    }

    @Test
    void withThreeWordsTwoAreNotEnoughButWithFourThreeAre() {
        assertThat(keys(search("filtro aceite motor"))).containsExactlyInAnyOrder("filtro-3", "filtro-diesel");

        List<String> four = keys(search("filtro aceite motor sintetico"));
        // 3 de 4 = 0,75 ≥ 0,7; «filtro aceite» (2 de 4) queda fuera.
        assertThat(four).containsExactlyInAnyOrder("filtro-3", "filtro-diesel");
        assertThat(keys(search("filtro aceite"))).containsExactlyInAnyOrder("filtro-3", "filtro-2", "filtro-diesel");
    }

    @Test
    void facetsAddUpToTheTotalInTheSameStatementAndWithAFacetFilter() {
        SearchResult result = service.search(SearchQuery.builder().q("pastillas freno mazda").page(0).size(24)
                .build(), null);

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.facets().categories()).singleElement()
                .satisfies(category -> assertThat(category.count()).isEqualTo(result.total()));
        assertThat(result.facets().inStock()).isEqualTo(2);

        SearchResult inStock = service.search(SearchQuery.builder().q("pastillas freno mazda").inStock(true).page(0)
                .size(24).build(), null);
        assertThat(inStock.total()).isEqualTo(2);
        // La faceta de categoría cuenta con el filtro de stock (no es el suyo): cuadra con el total.
        assertThat(inStock.facets().categories().stream().mapToLong(SearchResult.IdCount::count).sum())
                .isEqualTo(inStock.total());
        // La de stock omite su propio filtro: los 2 con stock dentro de los 3 que cumplen lo demás.
        assertThat(inStock.facets().inStock()).isEqualTo(2);
    }
}
