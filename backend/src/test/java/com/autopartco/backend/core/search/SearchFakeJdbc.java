package com.autopartco.backend.core.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link JdbcClient} de prueba sin BD: registra cada sentencia con sus parámetros (con nombre o posicionales) y
 * responde con filas programadas por un predicado sobre el SQL, pasando por el {@link RowMapper} real.
 */
final class SearchFakeJdbc {
    record Call(String sql, Map<String, Object> params, List<Object> positional) {
    }

    private record Rule(Predicate<String> sql, Function<Call, List<Map<String, Object>>> rows, Object scalar) {
    }

    final JdbcClient client = mock(JdbcClient.class);
    final List<Call> calls = new ArrayList<>();
    private final List<Rule> rules = new ArrayList<>();

    SearchFakeJdbc() {
        when(client.sql(anyString())).thenAnswer(invocation -> statement(invocation.getArgument(0)));
    }

    /** Filas para las consultas cuyo SQL cumple {@code sql} (la última regla que coincide gana). */
    SearchFakeJdbc rows(Predicate<String> sql, Function<Call, List<Map<String, Object>>> rows) {
        rules.add(new Rule(sql, rows, null));
        return this;
    }

    /** Valor de {@code query(Class).single()} para las consultas cuyo SQL cumple {@code sql}. */
    SearchFakeJdbc scalar(Predicate<String> sql, Object value) {
        rules.add(new Rule(sql, null, value));
        return this;
    }

    List<Call> callsMatching(Predicate<String> sql) {
        return calls.stream().filter(call -> sql.test(call.sql())).toList();
    }

    private Optional<Rule> rule(String sql, boolean scalar) {
        Rule found = null;
        for (Rule rule : rules) {
            if ((rule.scalar() != null) == scalar && rule.sql().test(sql)) {
                found = rule;
            }
        }
        return Optional.ofNullable(found);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private JdbcClient.StatementSpec statement(String sql) throws Exception {
        Call call = new Call(sql, new LinkedHashMap<>(), new ArrayList<>());
        calls.add(call);
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
        when(spec.param(anyString(), any())).thenAnswer(invocation -> {
            call.params().put(invocation.getArgument(0), invocation.getArgument(1));
            return spec;
        });
        when(spec.params(anyMap())).thenAnswer(invocation -> {
            call.params().putAll(invocation.getArgument(0));
            return spec;
        });
        when(spec.params(anyList())).thenAnswer(invocation -> {
            call.positional().addAll(invocation.getArgument(0));
            return spec;
        });
        when(spec.query(any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<Object> mapper = invocation.getArgument(0);
            List<Map<String, Object>> rows = rule(sql, false).map(rule -> rule.rows().apply(call)).orElse(List.of());
            List<Object> mapped = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                mapped.add(mapper.mapRow(resultSet(rows.get(i)), i));
            }
            JdbcClient.MappedQuerySpec query = mock(JdbcClient.MappedQuerySpec.class);
            when(query.list()).thenReturn(mapped);
            when(query.optional()).thenReturn(mapped.stream().findFirst());
            return query;
        });
        when(spec.query(any(Class.class))).thenAnswer(invocation -> {
            Optional<Rule> scalar = rule(sql, true);
            List<Map<String, Object>> rows = rule(sql, false).map(rule -> rule.rows().apply(call)).orElse(List.of());
            JdbcClient.MappedQuerySpec query = mock(JdbcClient.MappedQuerySpec.class);
            Object value = scalar.map(Rule::scalar).orElse(null);
            List<Object> list = scalar.isPresent() ? List.of(value)
                    : rows.stream().map(row -> row.values().iterator().next()).toList();
            when(query.single()).thenReturn(value);
            when(query.list()).thenReturn(list);
            when(query.optional()).thenReturn(Optional.ofNullable(value));
            return query;
        });
        when(spec.update()).thenReturn(1);
        return spec;
    }

    @SuppressWarnings("unchecked")
    private static ResultSet resultSet(Map<String, Object> row) throws Exception {
        ResultSet result = mock(ResultSet.class);
        boolean[] lastNull = {false};
        when(result.getObject(anyString(), any(Class.class))).thenAnswer(invocation -> {
            Object value = row.get(invocation.<String>getArgument(0));
            lastNull[0] = value == null;
            return value;
        });
        when(result.getString(anyString())).thenAnswer(invocation -> {
            Object value = row.get(invocation.<String>getArgument(0));
            lastNull[0] = value == null;
            return value == null ? null : value.toString();
        });
        when(result.getInt(anyString())).thenAnswer(invocation -> {
            Object value = row.get(invocation.<String>getArgument(0));
            lastNull[0] = value == null;
            return value == null ? 0 : ((Number) value).intValue();
        });
        when(result.getLong(anyString())).thenAnswer(invocation -> {
            Object value = row.get(invocation.<String>getArgument(0));
            lastNull[0] = value == null;
            return value == null ? 0L : ((Number) value).longValue();
        });
        when(result.getDouble(anyString())).thenAnswer(invocation -> {
            Object value = row.get(invocation.<String>getArgument(0));
            lastNull[0] = value == null;
            return value == null ? 0.0 : ((Number) value).doubleValue();
        });
        when(result.getBoolean(anyString())).thenAnswer(invocation -> {
            Object value = row.get(invocation.<String>getArgument(0));
            lastNull[0] = value == null;
            return Boolean.TRUE.equals(value);
        });
        when(result.getBigDecimal(anyString())).thenAnswer(invocation -> {
            Object value = row.get(invocation.<String>getArgument(0));
            lastNull[0] = value == null;
            return (BigDecimal) value;
        });
        when(result.wasNull()).thenAnswer(invocation -> lastNull[0]);
        return result;
    }
}
