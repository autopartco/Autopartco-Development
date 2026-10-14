package com.autopartco.backend.core.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.autopartco.backend.core.inventory.internal.InventoryRepository;

class StockServiceTest {
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    private JdbcClient jdbcClient;
    private JdbcClient.StatementSpec statement;
    private StockService service;
    private final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);

    @BeforeEach
    void setUp() {
        jdbcClient = mock(JdbcClient.class);
        statement = mock(JdbcClient.StatementSpec.class);
        when(jdbcClient.sql(sql.capture())).thenReturn(statement);
        when(statement.param(anyString(), any())).thenReturn(statement);
        service = new StockService(mock(InventoryRepository.class), jdbcClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void decrementUsesConditionalAtomicUpdate() {
        UUID inventoryId = UUID.randomUUID();
        JdbcClient.MappedQuerySpec<StockDecrement> mapped = mock(JdbcClient.MappedQuerySpec.class);
        when(statement.query(any(RowMapper.class))).thenReturn(mapped);
        when(mapped.list()).thenReturn(List.of(new StockDecrement(inventoryId, 4, new BigDecimal("90000.00"))));

        Optional<StockDecrement> result = service.decrementIfAvailable(BRANCH_ID, PRODUCT_ID, 2);

        assertThat(result).contains(new StockDecrement(inventoryId, 4, new BigDecimal("90000.00")));
        String statementSql = sql.getValue();
        assertThat(statementSql)
                .contains("UPDATE inventory")
                .contains("stock = stock - :units")
                .contains("stock >= :units")
                .contains("version = version + 1")
                .contains("deleted_at IS NULL")
                .contains("RETURNING id, stock, price");
        verify(statement).param("units", 2);
        verify(statement).param("branchId", BRANCH_ID);
        verify(statement).param("productId", PRODUCT_ID);
    }

    @Test
    @SuppressWarnings("unchecked")
    void decrementReturnsEmptyWhenNoRowIsUpdated() {
        JdbcClient.MappedQuerySpec<StockDecrement> mapped = mock(JdbcClient.MappedQuerySpec.class);
        when(statement.query(any(RowMapper.class))).thenReturn(mapped);
        when(mapped.list()).thenReturn(List.of());

        assertThat(service.decrementIfAvailable(BRANCH_ID, PRODUCT_ID, 5)).isEmpty();
    }

    @Test
    void decrementRejectsNonPositiveUnits() {
        JdbcClient untouched = mock(JdbcClient.class);
        StockService guarded = new StockService(mock(InventoryRepository.class), untouched);

        assertThatThrownBy(() -> guarded.decrementIfAvailable(BRANCH_ID, PRODUCT_ID, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> guarded.decrementIfAvailable(BRANCH_ID, PRODUCT_ID, -1))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(untouched);
    }

    @Test
    @SuppressWarnings("unchecked")
    void availableStockReturnsCurrentStockOfLiveListing() {
        JdbcClient.MappedQuerySpec<Integer> mapped = mock(JdbcClient.MappedQuerySpec.class);
        when(statement.query(eq(Integer.class))).thenReturn(mapped);
        when(mapped.optional()).thenReturn(Optional.of(1));

        assertThat(service.availableStock(BRANCH_ID, PRODUCT_ID)).isEqualTo(1);
        assertThat(sql.getValue()).contains("SELECT stock").contains("deleted_at IS NULL");
    }

    @Test
    @SuppressWarnings("unchecked")
    void availableStockIsZeroWithoutListing() {
        JdbcClient.MappedQuerySpec<Integer> mapped = mock(JdbcClient.MappedQuerySpec.class);
        when(statement.query(eq(Integer.class))).thenReturn(mapped);
        when(mapped.optional()).thenReturn(Optional.empty());

        assertThat(service.availableStock(BRANCH_ID, PRODUCT_ID)).isZero();
    }
}
