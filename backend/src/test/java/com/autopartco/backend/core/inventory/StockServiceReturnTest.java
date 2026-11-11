package com.autopartco.backend.core.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.autopartco.backend.core.inventory.internal.InventoryRepository;

class StockServiceReturnTest {
    private static final UUID FIRST = UUID.randomUUID();
    private static final UUID SECOND = UUID.randomUUID();

    @Test
    @SuppressWarnings("unchecked")
    void locksInCheckoutOrderAndAddsUnitsBack() {
        JdbcClient jdbcClient = mock(JdbcClient.class);
        List<String> sql = new ArrayList<>();
        List<Object[]> params = new ArrayList<>();
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
        when(jdbcClient.sql(anyString())).thenAnswer(invocation -> {
            sql.add(invocation.getArgument(0));
            return statement;
        });
        when(statement.param(anyString(), any())).thenAnswer(invocation -> {
            params.add(new Object[] {invocation.getArgument(0), invocation.getArgument(1)});
            return statement;
        });
        JdbcClient.MappedQuerySpec<UUID> mapped = mock(JdbcClient.MappedQuerySpec.class);
        when(statement.query(any(RowMapper.class))).thenReturn(mapped);
        // La BD devuelve el orden de bloqueo (branch_id, product_id), no el de entrada.
        when(mapped.list()).thenReturn(List.of(SECOND, FIRST));
        when(statement.update()).thenReturn(1);

        new StockService(mock(InventoryRepository.class), jdbcClient, mock(StockLedger.class), event -> { }).returnStock(List.of(
                new StockReturn(FIRST, 2), new StockReturn(SECOND, 1), new StockReturn(FIRST, 1)));

        assertThat(sql.get(0)).contains("FOR UPDATE").contains("ORDER BY branch_id, product_id");
        assertThat(sql.subList(1, 3)).allSatisfy(statementSql -> assertThat(statementSql)
                .contains("stock = stock + :units").contains("version = version + 1"));
        assertThat(params.get(0)[1]).isEqualTo(List.of(FIRST, SECOND));
        assertThat(params.subList(1, params.size())).extracting(entry -> entry[0] + "=" + entry[1])
                .containsExactly("units=1", "inventoryId=" + SECOND, "units=3", "inventoryId=" + FIRST);
    }

    @Test
    void emptyReturnDoesNothing() {
        JdbcClient jdbcClient = mock(JdbcClient.class);

        new StockService(mock(InventoryRepository.class), jdbcClient, mock(StockLedger.class), event -> { }).returnStock(List.of());

        verify(jdbcClient, never()).sql(anyString());
    }
}
