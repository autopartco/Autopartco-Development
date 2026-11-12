package com.autopartco.backend.marketplace.reviews;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.notification.EmailService;
import com.autopartco.backend.shared.config.AppProperties;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Correo «¿Cómo te fue con tu pedido?» al comprador cuando una venta de su pedido se entrega. Una sola vez por pedido:
 * la marca es un registro de auditoría {@value #ACTION} sobre el pedido (sin esquema nuevo), que se consulta antes de
 * enviar y se escribe después. Dos entregas simultáneas del mismo pedido podrían duplicarlo en teoría; la entrega de
 * cada venta ocurre en su propia transacción y es muy poco probable que coincidan.
 */
@Service
@AllArgsConstructor
@Slf4j
public class ReviewInvitationService {
    static final String ACTION = "REVIEW_INVITATION_SENT";
    static final String ENTITY_TYPE = "ORDER";

    /** Comprador del pedido entregado: primera cuenta de la persona, con nombre y número de pedido. */
    static final String RECIPIENT_SQL = """
            SELECT a.id AS account_id, a.email, p.name, o.order_number
            FROM orders o
            JOIN persons p ON p.id = o.person_id
            JOIN accounts a ON a.id_person = o.person_id AND a.deleted_at IS NULL
            WHERE o.id = :orderId AND o.deleted_at IS NULL
              AND EXISTS (SELECT 1 FROM sales s JOIN states st ON st.id = s.state_id
                          WHERE s.order_id = o.id AND s.deleted_at IS NULL AND st.code = 'DELIVERED')
            ORDER BY a.created_at, a.id
            LIMIT 1
            """;

    record Recipient(UUID accountId, String email, String givenName, long orderNumber) {
    }

    private final JdbcClient jdbcClient;
    private final AuditService auditService;
    private final EmailService emailService;
    private final AppProperties appProperties;

    /** Envía la invitación si el pedido está entregado y aún no se envió. Devuelve si la envió. */
    public boolean invite(UUID orderId) {
        if (auditService.exists(ACTION, ENTITY_TYPE, orderId)) {
            return false;
        }
        Optional<Recipient> recipient = recipient(orderId);
        if (recipient.isEmpty()) {
            return false;
        }
        Recipient to = recipient.get();
        emailService.sendReviewInvitation(to.email(), firstName(to.givenName()), to.orderNumber(), link(orderId));
        auditService.record(to.accountId(), ACTION, ENTITY_TYPE, orderId, Map.of("orderNumber", to.orderNumber()));
        return true;
    }

    Optional<Recipient> recipient(UUID orderId) {
        return jdbcClient.sql(RECIPIENT_SQL).param("orderId", orderId)
                .query((rs, row) -> new Recipient(rs.getObject("account_id", UUID.class), rs.getString("email"),
                        rs.getString("name"), rs.getLong("order_number")))
                .optional();
    }

    String link(UUID orderId) {
        return UriComponentsBuilder.fromUriString(appProperties.frontendBaseUrl().replaceAll("/+$", ""))
                .path("/cuenta/calificar")
                .queryParam("pedido", orderId)
                .build()
                .toUriString();
    }

    private static String firstName(String name) {
        return name == null || name.isBlank() ? "" : name.trim().split("\\s+")[0];
    }
}
