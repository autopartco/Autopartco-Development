package com.autopartco.backend.core.order;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.notification.NotificationService;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/**
 * Estado de una venta: bloqueo de la fila, transiciones permitidas y aviso al cliente.
 * No comprueba permisos; lo hace el caso de uso del producto.
 */
@Service
@AllArgsConstructor
public class SaleStatusService {
    private final JdbcTemplate jdbcTemplate;
    private final SaleQueries saleQueries;
    private final NotificationService notificationService;

    @Transactional
    public SaleDetailResponse updateStatus(UUID storeId, UUID saleId, SaleStatusRequest request) {
        String currentStatus = lockSaleStatus(storeId, saleId);
        String nextStatus = request.status().toUpperCase(Locale.ROOT);
        if (!isOrderTransitionAllowed(currentStatus, nextStatus)) {
            throw new ConflictException(
                    "SELLER_ORDER_STATUS_TRANSITION_INVALID",
                    "No se puede cambiar el pedido de " + currentStatus + " a " + nextStatus + "."
            );
        }
        updateSaleStatus(saleId, nextStatus);
        notifyCustomer(saleId, "Pedido actualizado", "Tu pedido cambió al estado " + nextStatus + ".");
        return saleQueries.detail(storeId, saleId);
    }

    /** Bloquea la venta del almacén ({@code FOR UPDATE}) y devuelve su estado; si no existe, {@code SELLER_ORDER_NOT_FOUND}. */
    String lockSaleStatus(UUID storeId, UUID saleId) {
        return jdbcTemplate.query("""
                SELECT status.code
                FROM sales sale
                JOIN branches branch ON branch.id = sale.branch_id
                JOIN states status ON status.id = sale.state_id
                WHERE branch.store_id = ?
                  AND sale.id = ?
                  AND sale.deleted_at IS NULL
                FOR UPDATE OF sale
                """, (result, row) -> result.getString(1), storeId, saleId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_ORDER_NOT_FOUND",
                        "No se encontró la venta de este almacén."
                ));
    }

    void updateSaleStatus(UUID saleId, String status) {
        int updated = jdbcTemplate.update("""
                UPDATE sales
                SET state_id = (SELECT id FROM states WHERE code = ?),
                    updated_at = now()
                WHERE id = ?
                  AND deleted_at IS NULL
                """, status, saleId);
        if (updated != 1) {
            throw new NotFoundException("SELLER_ORDER_NOT_FOUND", "No se encontró la venta.");
        }
    }

    /** Notifica a las cuentas del cliente de la venta (venta → pedido → persona → cuenta) en la misma transacción. */
    void notifyCustomer(UUID saleId, String title, String message) {
        List<UUID> accountIds = jdbcTemplate.query("""
                SELECT account.id
                FROM sales sale
                JOIN orders customer_order ON customer_order.id = sale.order_id
                JOIN accounts account ON account.id_person = customer_order.person_id
                WHERE sale.id = ?
                """, (result, row) -> result.getObject(1, UUID.class), saleId);
        accountIds.forEach(accountId -> notificationService.notify(accountId, title, message));
    }

    static boolean isOrderTransitionAllowed(String current, String next) {
        return switch (current) {
            case "PENDING" -> next.equals("CONFIRMED") || next.equals("CANCELLED");
            case "CONFIRMED" -> next.equals("PREPARING") || next.equals("CANCELLED");
            default -> false;
        };
    }
}
