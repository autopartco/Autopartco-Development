package com.autopartco.backend.core.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Atribución de pedidos con un {@link JdbcClient} simulado: el visitante anónimo guardado en
 * {@code order_attributions} se busca en {@code ad_events} con el mismo hash con el que {@code AdEvents} registró sus
 * clics ({@link AdVisitors#ofAnonymous}).
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class AdOrderAttributionTest {
    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");
    private static final UUID ORDER_ID = UUID.randomUUID();

    private final AdVisitors visitors = new AdVisitors("test-salt");
    private final List<Statement> statements = new ArrayList<>();

    record Statement(String sql, Map<String, Object> params) {
    }

    /** JdbcClient que registra las sentencias y responde a {@code VISITORS_SQL} con {@code rows}. */
    private JdbcClient jdbc(List<Map<String, Object>> rows) {
        JdbcClient client = mock(JdbcClient.class);
        when(client.sql(anyString())).thenAnswer(invocation -> {
            Statement statement = new Statement(invocation.getArgument(0), new LinkedHashMap<>());
            statements.add(statement);
            JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
            when(spec.param(anyString(), any())).thenAnswer(call -> {
                statement.params().put(call.getArgument(0), call.getArgument(1));
                return spec;
            });
            when(spec.update()).thenReturn(1);
            when(spec.query(any(RowMapper.class))).thenAnswer(call -> {
                RowMapper<Object> mapper = call.getArgument(0);
                JdbcClient.MappedQuerySpec mapped = mock(JdbcClient.MappedQuerySpec.class);
                when(mapped.list()).thenAnswer(ignored -> {
                    List<Object> result = new ArrayList<>();
                    for (Map<String, Object> row : rows) {
                        result.add(mapper.mapRow(resultSet(row), result.size()));
                    }
                    return result;
                });
                return mapped;
            });
            return spec;
        });
        return client;
    }

    private static ResultSet resultSet(Map<String, Object> columns) {
        return mock(ResultSet.class, invocation -> invocation.getArguments().length > 0
                && invocation.getArgument(0) instanceof String column ? columns.get(column) : null);
    }

    private AdOrderAttribution attribution(JdbcClient client) {
        return new AdOrderAttribution(client, visitors, new TransactionTemplate(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Statement attributeStatement() {
        return statements.stream().filter(statement -> statement.sql().equals(AdOrderAttribution.ATTRIBUTE_SQL))
                .findFirst().orElseThrow();
    }

    @Test
    void anonymousBuyerIsMatchedWithTheSameHashAsItsAdClicks() {
        UUID anonymousId = UUID.fromString("3f2b8c1e-4d5a-4b6c-8d7e-9f0a1b2c3d4e");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("account_id", null);
        row.put("anonymous_id", anonymousId);

        int recorded = attribution(jdbc(List.of(row))).attribute(ORDER_ID);

        assertThat(recorded).isEqualTo(1);
        assertThat(statements.getFirst().sql()).isEqualTo(AdOrderAttribution.VISITORS_SQL);
        assertThat(AdOrderAttribution.VISITORS_SQL).contains("left join order_attributions oa on oa.order_id = o.id");
        Statement insert = attributeStatement();
        // Lo que AdEvents guardó al recibir el clic en /events/ads con el id del navegador (crypto.randomUUID).
        String clickHash = visitors.ofAnonymous("3f2b8c1e-4d5a-4b6c-8d7e-9f0a1b2c3d4e");
        assertThat((List<String>) insert.params().get("visitors")).containsExactly(clickHash);
        assertThat(insert.params().get("orderId")).isEqualTo(ORDER_ID);
        assertThat(insert.params().get("day")).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(insert.params().get("since")).isEqualTo(OffsetDateTime.parse("2026-11-04T15:00:00Z"));
    }

    @Test
    void uppercaseOrPaddedBrowserIdsProduceTheSameHash() {
        String stored = UUID.fromString("3F2B8C1E-4D5A-4B6C-8D7E-9F0A1B2C3D4E").toString();

        assertThat(visitors.ofAnonymous(" 3F2B8C1E-4D5A-4B6C-8D7E-9F0A1B2C3D4E "))
                .isEqualTo(visitors.ofAnonymous(stored))
                .hasSize(64);
        // Otro tipo de visitante (cuenta) nunca coincide con el anónimo.
        assertThat(visitors.ofAccount(UUID.fromString(stored))).isNotEqualTo(visitors.ofAnonymous(stored));
    }

    @Test
    void accountAndAnonymousVisitorsAreBothSearched() {
        UUID accountId = UUID.randomUUID();
        UUID anonymousId = UUID.randomUUID();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("account_id", accountId);
        row.put("anonymous_id", anonymousId);

        attribution(jdbc(List.of(row))).attribute(ORDER_ID);

        assertThat((List<String>) attributeStatement().params().get("visitors")).containsExactly(
                visitors.ofAccount(accountId), visitors.ofAnonymous(anonymousId.toString()));
    }

    @Test
    void orderWithoutAccountOrAnonymousIdIsNotAttributed() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("account_id", null);
        row.put("anonymous_id", null);

        assertThat(attribution(jdbc(List.of(row))).attribute(ORDER_ID)).isZero();
        assertThat(statements).extracting(Statement::sql).containsExactly(AdOrderAttribution.VISITORS_SQL);
    }
}
