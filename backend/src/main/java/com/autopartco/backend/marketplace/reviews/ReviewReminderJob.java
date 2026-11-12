package com.autopartco.backend.marketplace.reviews;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.autopartco.backend.core.notification.EmailService;
import com.autopartco.backend.core.settings.SettingsQueries;
import com.autopartco.backend.shared.config.AppProperties;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Recordatorios por correo para calificar lo recibido: el intento 1 a los {@value #FIRST_DAYS} días de entregada la
 * venta y el 2 a los {@value #SECOND_DAYS}, solo si aún falta calificar el almacén o algún producto de esa venta y la
 * persona no desactivó {@code notifications.reviewReminders}. Un correo por pedido y ejecución; cada envío queda en
 * {@code review_reminders} (único por venta, canal e intento), así que repetir la tarea no duplica correos.
 * {@code @EnableScheduling} lo activa {@code core.payments.internal.PaymentsConfig}.
 */
@Component
@AllArgsConstructor
@Slf4j
public class ReviewReminderJob {
    static final int FIRST_DAYS = 3;
    static final int SECOND_DAYS = 10;
    /** Después de esto ya no se insiste (coincide con la ventana de «Por calificar»). */
    static final int GIVE_UP_DAYS = 30;
    static final int BATCH = 200;
    static final String CHANNEL = "EMAIL";

    /** Ventas entregadas que tocan recordatorio {@code :attempt} y siguen con algo por calificar. */
    static final String DUE_SQL = """
            WITH delivered AS (
                SELECT s.id AS sale_id, s.order_id, b.store_id, o.person_id, o.order_number,
                       coalesce((SELECT max(sh.delivered_at) FROM shipments sh WHERE sh.sale_id = s.id), s.updated_at)
                           AS delivered_at
                FROM sales s
                JOIN states ss ON ss.id = s.state_id AND ss.code = 'DELIVERED'
                JOIN orders o ON o.id = s.order_id AND o.deleted_at IS NULL
                JOIN branches b ON b.id = s.branch_id
                WHERE s.deleted_at IS NULL
            )
            SELECT d.sale_id, d.order_id, d.order_number, a.id AS account_id, a.email, p.name
            FROM delivered d
            JOIN persons p ON p.id = d.person_id
            JOIN LATERAL (
                SELECT acc.id, acc.email FROM accounts acc
                WHERE acc.id_person = d.person_id AND acc.deleted_at IS NULL
                ORDER BY acc.created_at, acc.id LIMIT 1
            ) a ON true
            WHERE d.delivered_at <= now() - make_interval(days => :minDays)
              AND d.delivered_at > now() - make_interval(days => :maxDays)
              AND NOT EXISTS (SELECT 1 FROM review_reminders rr
                              WHERE rr.sale_id = d.sale_id AND rr.channel = 'EMAIL' AND rr.attempt >= :attempt)
              AND (
                  NOT EXISTS (SELECT 1 FROM store_reviews r
                              WHERE r.store_id = d.store_id AND r.person_id = d.person_id AND r.order_id = d.order_id)
                  OR EXISTS (
                      SELECT 1 FROM order_details od
                      LEFT JOIN inventory i ON i.id = od.inventory_id
                      WHERE od.sale_id = d.sale_id AND od.deleted_at IS NULL
                        AND NOT EXISTS (SELECT 1 FROM product_reviews pr
                                        WHERE pr.person_id = d.person_id AND pr.deleted_at IS NULL
                                          AND pr.product_id = coalesce(od.product_id, i.product_id)))
              )
            ORDER BY d.delivered_at, d.sale_id
            LIMIT :limit
            """;

    record Due(UUID saleId, UUID orderId, long orderNumber, UUID accountId, String email, String name) {
    }

    private final JdbcClient jdbcClient;
    private final EmailService emailService;
    private final SettingsQueries settingsQueries;
    private final AppProperties appProperties;

    @Scheduled(cron = "0 0 10 * * *", zone = "America/Bogota")
    public void run() {
        int sent = remind(1, FIRST_DAYS, SECOND_DAYS) + remind(2, SECOND_DAYS, GIVE_UP_DAYS);
        if (sent > 0) {
            log.info("Sent {} review reminders", sent);
        }
    }

    /** Envía el intento {@code attempt} a lo entregado hace entre {@code minDays} y {@code maxDays}. Devuelve los correos. */
    int remind(int attempt, int minDays, int maxDays) {
        List<Due> due = jdbcClient.sql(DUE_SQL)
                .param("attempt", attempt)
                .param("minDays", minDays)
                .param("maxDays", maxDays)
                .param("limit", BATCH)
                .query((rs, row) -> new Due(rs.getObject("sale_id", UUID.class), rs.getObject("order_id", UUID.class),
                        rs.getLong("order_number"), rs.getObject("account_id", UUID.class), rs.getString("email"),
                        rs.getString("name")))
                .list();
        return process(attempt, due);
    }

    /** Agrupa por pedido, envía (si la preferencia lo permite) y marca cada venta con el intento. */
    int process(int attempt, List<Due> due) {
        // Un correo por pedido aunque tenga varias ventas pendientes.
        Map<UUID, List<Due>> byOrder = new LinkedHashMap<>();
        due.forEach(item -> byOrder.computeIfAbsent(item.orderId(), ignored -> new java.util.ArrayList<>()).add(item));
        int sent = 0;
        for (List<Due> sales : byOrder.values()) {
            Due first = sales.getFirst();
            boolean wanted = settingsQueries.accountSettings(first.accountId()).notifications().reviewReminders();
            if (wanted) {
                try {
                    emailService.sendReviewReminder(first.email(), firstName(first.name()), first.orderNumber(),
                            ReviewInvitationService.absolute(appProperties, ReviewInvitationService.path(first.orderId())),
                            ReviewInvitationService.absolute(appProperties, "/cuenta/preferencias"));
                    sent++;
                } catch (RuntimeException failure) {
                    log.warn("Could not send review reminder ({})", failure.getClass().getSimpleName());
                    continue;
                }
            }
            // También se marca si la persona los desactivó: así no se reevalúa cada día.
            for (Due sale : sales) {
                jdbcClient.sql("""
                        INSERT INTO review_reminders (sale_id, channel, attempt) VALUES (:saleId, :channel, :attempt)
                        ON CONFLICT (sale_id, channel, attempt) DO NOTHING
                        """).param("saleId", sale.saleId()).param("channel", CHANNEL).param("attempt", attempt).update();
            }
        }
        return sent;
    }

    private static String firstName(String name) {
        return name == null || name.isBlank() ? "" : name.trim().split("\\s+")[0];
    }
}
