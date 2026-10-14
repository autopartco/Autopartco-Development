package com.autopartco.backend.core.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link JdbcClient} simulado para pruebas unitarias sin base de datos: registra cada sentencia (SQL y parámetros con
 * nombre, en orden) y responde según una función que recibe la sentencia.
 *
 * <p>Las consultas con {@code RowMapper} se resuelven invocando el mapper real del código sobre los
 * {@link ResultSet} simulados que devuelve {@code rows}; las de columna única ({@code query(Class)}) devuelven
 * {@code scalar}.</p>
 */
@SuppressWarnings({"unchecked", "rawtypes"})
final class RecordingJdbcClient {
    /** Una sentencia ejecutada. */
    record Statement(String sql, Map<String, Object> params) {
        Object param(String name) {
            return params.get(name);
        }
    }

    private final List<Statement> statements = new ArrayList<>();
    private final JdbcClient client = mock(JdbcClient.class);
    private Function<Statement, Object> scalar = statement -> null;
    private Function<Statement, List<ResultSet>> rows = statement -> List.of();

    RecordingJdbcClient() {
        when(client.sql(anyString())).thenAnswer(invocation -> statementSpec(invocation.getArgument(0)));
    }

    JdbcClient client() {
        return client;
    }

    List<Statement> statements() {
        return statements;
    }

    RecordingJdbcClient onScalar(Function<Statement, Object> answer) {
        this.scalar = answer;
        return this;
    }

    RecordingJdbcClient onRows(Function<Statement, List<ResultSet>> answer) {
        this.rows = answer;
        return this;
    }

    /** {@link ResultSet} simulado de una fila: columna → valor (los numéricos primitivos se convierten). */
    static ResultSet row(Map<String, ?> columns) {
        return mock(ResultSet.class, invocation -> {
            if (invocation.getArguments().length == 0 || !(invocation.getArgument(0) instanceof String column)) {
                return null;
            }
            Object value = columns.get(column);
            return switch (invocation.getMethod().getName()) {
                case "getLong" -> value == null ? 0L : ((Number) value).longValue();
                case "getInt" -> value == null ? 0 : ((Number) value).intValue();
                case "getString" -> value == null ? null : value.toString();
                default -> value;
            };
        });
    }

    private JdbcClient.StatementSpec statementSpec(String sql) {
        Statement statement = new Statement(sql, new LinkedHashMap<>());
        statements.add(statement);
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
        when(spec.param(anyString(), any())).thenAnswer(invocation -> {
            statement.params().put(invocation.getArgument(0), invocation.getArgument(1));
            return spec;
        });
        when(spec.param(anyString(), any(), anyInt())).thenAnswer(invocation -> {
            statement.params().put(invocation.getArgument(0), invocation.getArgument(1));
            return spec;
        });
        when(spec.update()).thenReturn(1);
        when(spec.query(any(Class.class))).thenAnswer(invocation -> {
            JdbcClient.MappedQuerySpec mapped = mock(JdbcClient.MappedQuerySpec.class);
            when(mapped.single()).thenAnswer(ignored -> scalar.apply(statement));
            return mapped;
        });
        when(spec.query(any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<Object> mapper = invocation.getArgument(0);
            JdbcClient.MappedQuerySpec mapped = mock(JdbcClient.MappedQuerySpec.class);
            when(mapped.list()).thenAnswer(ignored -> {
                List<Object> result = new ArrayList<>();
                int index = 0;
                for (ResultSet resultSet : rows.apply(statement)) {
                    result.add(mapper.mapRow(resultSet, index++));
                }
                return result;
            });
            return mapped;
        });
        return spec;
    }
}
