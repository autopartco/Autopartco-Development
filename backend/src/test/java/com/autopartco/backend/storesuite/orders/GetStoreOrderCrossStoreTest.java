package com.autopartco.backend.storesuite.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.order.SaleQueries;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.shared.error.NotFoundException;

/** IDOR entre almacenes: con permiso en el almacén A no se puede leer una venta del almacén B. */
@ExtendWith(MockitoExtension.class)
class GetStoreOrderCrossStoreTest {
    private static final String EMAIL = "staff-a@example.com";
    private static final UUID STORE_A = UUID.randomUUID();
    private static final UUID STORE_B = UUID.randomUUID();
    private static final UUID SALE_OF_B = UUID.randomUUID();

    @Mock
    private StoreAccess storeAccess;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private GetStoreOrder useCase() {
        return new GetStoreOrder(storeAccess, new SaleQueries(jdbcTemplate));
    }

    @Test
    void saleOfAnotherStoreThroughOwnStoreIsNotFound() {
        // El JdbcTemplate simulado devuelve lista vacía: la consulta acotada por almacén no encuentra la venta de B.
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> useCase().execute(STORE_A, SALE_OF_B, EMAIL));

        assertEquals("SELLER_ORDER_NOT_FOUND", exception.getCode());
        verify(storeAccess).requirePermission(EMAIL, STORE_A, StorePermissionCodes.ORDER_READ);
        verify(jdbcTemplate).query(contains("branch.store_id = ?"), any(RowMapper.class), eq(STORE_A), eq(SALE_OF_B));
        // No se consultan líneas ni envío de una venta que no pertenece al almacén.
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void anotherStoreWithoutPermissionIsForbiddenBeforeQuerying() {
        doThrow(new AccessDeniedException("Store permission denied: " + StorePermissionCodes.ORDER_READ))
                .when(storeAccess).requirePermission(EMAIL, STORE_B, StorePermissionCodes.ORDER_READ);

        assertThrows(AccessDeniedException.class, () -> useCase().execute(STORE_B, SALE_OF_B, EMAIL));

        verifyNoInteractions(jdbcTemplate);
    }
}
