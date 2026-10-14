package com.autopartco.backend.core.order;

import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/** Envío de una venta: registro, transiciones y efecto sobre el estado de la venta. No comprueba permisos. */
@Service
@AllArgsConstructor
public class ShipmentService {
    private final JdbcTemplate jdbcTemplate;
    private final SaleQueries saleQueries;
    private final SaleStatusService saleStatusService;

    @Transactional
    public SaleDetailResponse create(UUID storeId, UUID saleId, ShipmentCreateRequest request) {
        String saleStatus = saleStatusService.lockSaleStatus(storeId, saleId);
        if (!"PREPARING".equals(saleStatus)) {
            throw new ConflictException(
                    "SELLER_SHIPMENT_ORDER_NOT_PREPARING",
                    "Solo se puede registrar el envío cuando la venta está en preparación."
            );
        }
        if (Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM shipments WHERE sale_id = ?)",
                Boolean.class,
                saleId
        ))) {
            throw new ConflictException("SELLER_SHIPMENT_ALREADY_EXISTS", "La venta ya tiene un envío registrado.");
        }
        UUID shipmentId = jdbcTemplate.queryForObject("""
                INSERT INTO shipments (
                    sale_id, shipment_status_id, carrier, tracking_number
                )
                VALUES (
                    ?, (SELECT id FROM shipment_statuses WHERE code = 'PREPARING'),
                    ?, ?
                )
                RETURNING id
                """, UUID.class, saleId, request.carrier().trim(), request.trackingNumber().trim());
        jdbcTemplate.update("""
                INSERT INTO shipment_events (shipment_id, shipment_status_id, notes, event_at)
                VALUES (
                    ?, (SELECT id FROM shipment_statuses WHERE code = 'PREPARING'),
                    'Envío registrado', now()
                )
                """, shipmentId);
        saleStatusService.notifyCustomer(saleId, "Envío registrado", "Se registró el envío de tu pedido.");
        return saleQueries.detail(storeId, saleId);
    }

    @Transactional
    public SaleDetailResponse updateStatus(UUID storeId, UUID saleId, ShipmentStatusRequest request) {
        saleStatusService.lockSaleStatus(storeId, saleId);
        ShipmentRow shipment = jdbcTemplate.query("""
                SELECT shipment.id, shipment_status.code AS status
                FROM shipments shipment
                JOIN shipment_statuses shipment_status ON shipment_status.id = shipment.shipment_status_id
                WHERE shipment.sale_id = ?
                FOR UPDATE OF shipment
                """, (result, row) -> new ShipmentRow(
                result.getObject("id", UUID.class),
                result.getString("status")
        ), saleId).stream().findFirst().orElseThrow(() -> new NotFoundException(
                "SELLER_SHIPMENT_NOT_FOUND",
                "No se encontró el envío de esta venta."
        ));
        String nextStatus = request.status().toUpperCase(Locale.ROOT);
        if (!isShipmentTransitionAllowed(shipment.status(), nextStatus)) {
            throw new ConflictException(
                    "SELLER_SHIPMENT_STATUS_TRANSITION_INVALID",
                    "No se puede cambiar el envío de " + shipment.status() + " a " + nextStatus + "."
            );
        }
        jdbcTemplate.update("""
                UPDATE shipments
                SET shipment_status_id = (SELECT id FROM shipment_statuses WHERE code = ?),
                    shipped_at = CASE WHEN ? = 'DISPATCHED' THEN COALESCE(shipped_at, now()) ELSE shipped_at END,
                    delivered_at = CASE WHEN ? = 'DELIVERED' THEN now() ELSE delivered_at END,
                    updated_at = now()
                WHERE id = ?
                """, nextStatus, nextStatus, nextStatus, shipment.id());
        jdbcTemplate.update("""
                INSERT INTO shipment_events (shipment_id, shipment_status_id, notes, event_at)
                VALUES (?, (SELECT id FROM shipment_statuses WHERE code = ?), ?, now())
                """, shipment.id(), nextStatus, cleanNotes(request.notes()));
        if ("DISPATCHED".equals(nextStatus)) {
            saleStatusService.updateSaleStatus(saleId, "SHIPPED");
        } else if ("DELIVERED".equals(nextStatus)) {
            saleStatusService.updateSaleStatus(saleId, "DELIVERED");
        }
        saleStatusService.notifyCustomer(
                saleId,
                "Estado del envío actualizado",
                "Tu envío cambió al estado " + nextStatus + "."
        );
        return saleQueries.detail(storeId, saleId);
    }

    static boolean isShipmentTransitionAllowed(String current, String next) {
        return switch (current) {
            case "PREPARING" -> next.equals("DISPATCHED");
            case "DISPATCHED" -> next.equals("IN_TRANSIT")
                    || next.equals("OUT_FOR_DELIVERY")
                    || next.equals("DELIVERED");
            case "IN_TRANSIT" -> next.equals("OUT_FOR_DELIVERY") || next.equals("DELIVERED");
            case "OUT_FOR_DELIVERY" -> next.equals("DELIVERED");
            default -> false;
        };
    }

    private static String cleanNotes(String notes) {
        return notes == null || notes.isBlank() ? null : notes.trim();
    }

    private record ShipmentRow(UUID id, String status) {
    }
}
