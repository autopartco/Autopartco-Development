package com.autopartco.backend.core.inventory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link JdbcClient} de prueba: registra cada sentencia con sus parámetros y responde las consultas con filas
 * programadas por SQL (en orden), pasando por el {@link RowMapper} real.
 */
final class FakeJdbc {
    record Call(String sql, Map<String, Object> params) {
    }

    final JdbcClient client = mock(JdbcClient.class);
    final List<Call> calls = new ArrayList<>();
    private final Map<String, Deque<List<Map<String, Object>>>> results = new HashMap<>();

    FakeJdbc() {
        when(client.sql(anyString())).thenAnswer(invocation -> statement(invocation.getArgument(0)));
    }

    /** Programa la respuesta de la próxima ejecución de {@code sql}. Sin respuesta programada: sin filas. */
    @SafeVarargs
    final FakeJdbc returns(String sql, Map<String, Object>... rows) {
        results.computeIfAbsent(sql, key -> new ArrayDeque<>()).add(List.of(rows));
        return this;
    }

    List<Call> callsTo(String sql) {
        return calls.stream().filter(call -> call.sql().equals(sql)).toList();
    }

    List<String> sqlSequence() {
        return calls.stream().map(Call::sql).toList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private JdbcClient.StatementSpec statement(String sql) throws Exception {
        Call call = new Call(sql, new LinkedHashMap<>());
        calls.add(call);
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
        when(spec.param(anyString(), any())).thenAnswer(invocation -> {
            call.params().put(invocation.getArgument(0), invocation.getArgument(1));
            return spec;
        });
        when(spec.query(any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<Object> mapper = invocation.getArgument(0);
            Deque<List<Map<String, Object>>> queue = results.get(sql);
            List<Map<String, Object>> rows = queue == null || queue.isEmpty() ? List.of() : queue.poll();
            List<Object> mapped = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                mapped.add(mapper.mapRow(resultSet(rows.get(i)), i));
            }
            JdbcClient.MappedQuerySpec query = mock(JdbcClient.MappedQuerySpec.class);
            when(query.list()).thenReturn(mapped);
            when(query.optional()).thenReturn(mapped.stream().findFirst());
            return query;
        });
        when(spec.update()).thenReturn(1);
        return spec;
    }

    @SuppressWarnings("unchecked")
    private static ResultSet resultSet(Map<String, Object> row) throws Exception {
        ResultSet result = mock(ResultSet.class);
        List<Object> values = new ArrayList<>(row.values());
        when(result.getObject(anyString(), any(Class.class)))
                .thenAnswer(invocation -> row.get(invocation.<String>getArgument(0)));
        when(result.getString(anyString())).thenAnswer(invocation -> (String) row.get(invocation.getArgument(0)));
        when(result.getInt(anyString())).thenAnswer(invocation ->
                Optional.ofNullable((Integer) row.get(invocation.getArgument(0))).orElse(0));
        when(result.getString(anyInt())).thenAnswer(invocation ->
                (String) values.get(invocation.<Integer>getArgument(0) - 1));
        when(result.getLong(anyString())).thenAnswer(invocation ->
                Optional.ofNullable((Number) row.get(invocation.getArgument(0))).map(Number::longValue).orElse(0L));
        when(result.getBoolean(anyString())).thenAnswer(invocation ->
                Boolean.TRUE.equals(row.get(invocation.getArgument(0))));
        when(result.getBigDecimal(anyString())).thenAnswer(invocation ->
                (java.math.BigDecimal) row.get(invocation.getArgument(0)));
        return result;
    }
}
