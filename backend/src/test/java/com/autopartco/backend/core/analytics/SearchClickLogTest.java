package com.autopartco.backend.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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

class SearchClickLogTest {
    private static final Executor DIRECT = Runnable::run;

    @Test
    void invalidClicksAreNotQueuedAndFailuresNeverPropagate() {
        JdbcClient jdbc = mock(JdbcClient.class);
        SearchLog searchLog = mock(SearchLog.class);
        SearchClickLog clickLog = new SearchClickLog(jdbc, searchLog, DIRECT);

        clickLog.record(null, UUID.randomUUID(), 0);
        clickLog.record(UUID.randomUUID(), null, 0);
        clickLog.record(UUID.randomUUID(), UUID.randomUUID(), -1);
        verify(jdbc, never()).sql(anyString());

        UUID search = UUID.randomUUID();
        when(searchLog.resolve(search)).thenReturn(search);
        when(jdbc.sql(anyString())).thenThrow(new IllegalStateException("conexión caída"));
        assertThatCode(() -> clickLog.record(search, UUID.randomUUID(), 2)).doesNotThrowAnyException();
    }

    @Test
    void fullQueueNeverPropagates() {
        Executor rejecting = command -> {
            throw new RejectedExecutionException("cola llena");
        };
        assertThatCode(() -> new SearchClickLog(mock(JdbcClient.class), mock(SearchLog.class), rejecting)
                .record(UUID.randomUUID(), UUID.randomUUID(), 1)).doesNotThrowAnyException();
    }

    /** Contra PostgreSQL 17 real (esquema de {@code search/eval/schema.sql}). Sin Docker, se salta. */
    @Nested
    @Testcontainers(disabledWithoutDocker = true)
    class WithDatabase {
        @Container
        static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

        static JdbcTemplate jdbc;
        static SearchLog searchLog;
        static SearchClickLog clickLog;
        static final UUID STATE = UUID.randomUUID();
        static final UUID STORE = UUID.randomUUID();
        static final UUID PRODUCT = UUID.randomUUID();
        static final UUID OTHER_PRODUCT = UUID.randomUUID();

        @BeforeAll
        static void prepare() throws Exception {
            DataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                    POSTGRES.getPassword());
            try (Connection connection = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new EncodedResource(
                        new ClassPathResource("search/eval/schema.sql"), StandardCharsets.UTF_8));
            }
            jdbc = new JdbcTemplate(dataSource);
            jdbc.update("insert into states (id, code) values (?, 'ACTIVE')", STATE);
            jdbc.update("insert into store (id, business_name, state) values (?, 'Tienda', ?)", STORE, STATE);
            jdbc.update("insert into products (id, store_id, name, state_id) values (?, ?, 'Bujia', ?)",
                    PRODUCT, STORE, STATE);
            jdbc.update("insert into products (id, store_id, name, state_id, deleted_at) values (?, ?, 'Borrado', ?, "
                    + "now())", OTHER_PRODUCT, STORE, STATE);
            JdbcClient client = JdbcClient.create(dataSource);
            searchLog = new SearchLog(client, DIRECT);
            clickLog = new SearchClickLog(client, searchLog, DIRECT);
        }

        @BeforeEach
        void clean() {
            jdbc.execute("truncate search_clicks, marketplace_searches");
        }

        private static void search(UUID id, String session, String query) {
            searchLog.record(new MarketplaceSearch(query, null, null, null, null, null, null, 4, session, false, id));
        }

        private static List<UUID> clickedSearches() {
            return jdbc.queryForList("select search_id from search_clicks", UUID.class);
        }

        @Test
        void searchIsInsertedWithTheIdTheClientReceivedAndAClickIsStored() {
            UUID id = UUID.randomUUID();
            search(id, "sesion", "bujia");

            clickLog.record(id, PRODUCT, 2);

            assertThat(jdbc.queryForObject("select count(*) from marketplace_searches where id = ?", Integer.class,
                    id)).isEqualTo(1);
            assertThat(jdbc.queryForMap("select position from search_clicks where search_id = ?", id))
                    .containsEntry("position", 2);
        }

        @Test
        void clickOfADedupedSearchIsAssociatedToTheExistingSearch() {
            UUID first = UUID.randomUUID();
            UUID repeated = UUID.randomUUID();
            search(first, "sesion", "bujia");
            search(repeated, "sesion", "Bujía");

            // La repetida no se insertó, pero su clic no se pierde: queda en la búsqueda existente.
            assertThat(jdbc.queryForObject("select count(*) from marketplace_searches", Integer.class)).isEqualTo(1);
            clickLog.record(repeated, PRODUCT, 0);

            assertThat(clickedSearches()).containsExactly(first);
        }

        @Test
        void withoutSessionThereIsNoDedupAndEachIdKeepsItsOwnClicks() {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            search(first, null, "bujia");
            search(second, null, "bujia");

            clickLog.record(second, PRODUCT, 1);

            assertThat(jdbc.queryForObject("select count(*) from marketplace_searches", Integer.class)).isEqualTo(2);
            assertThat(clickedSearches()).containsExactly(second);
        }

        @Test
        void unknownSearchOrProductAreDiscardedAndAProductCountsOncePerSearch() {
            UUID id = UUID.randomUUID();
            search(id, "sesion", "bujia");

            clickLog.record(UUID.randomUUID(), PRODUCT, 0);
            clickLog.record(id, UUID.randomUUID(), 0);
            clickLog.record(id, OTHER_PRODUCT, 0);
            assertThat(clickedSearches()).isEmpty();

            clickLog.record(id, PRODUCT, 0);
            clickLog.record(id, PRODUCT, 3);
            assertThat(clickedSearches()).containsExactly(id);
        }
    }
}
