package com.autopartco.backend.core.advertising;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.autopartco.backend.shared.events.OrderPaid;

import lombok.extern.slf4j.Slf4j;

/**
 * Atribución de pedidos a anuncios: tras el commit de {@link OrderPaid}, registra un evento {@code ORDER} por cada
 * producto del pedido que el mismo visitante o la misma cuenta clicó en un anuncio en los {@value #WINDOW_DAYS} días
 * previos (último clic gana; uno por producto). El visitante del pedido sale de las cuentas de la persona compradora y,
 * si existe, de {@code order_attributions.anonymous_id}. {@code amount} = suma de las líneas del producto.
 * <p>
 * Corre en una transacción nueva y nunca lanza: un fallo se registra y no afecta al pago. La deduplicación por
 * {@code order_id} ({@code uq_ade_dedupe}) evita duplicados si el evento llega dos veces.
 */
@Slf4j
@Component
public class AdOrderAttribution {
    static final int WINDOW_DAYS = 7;

    static final String VISITORS_SQL = """
            select a.id as account_id, oa.anonymous_id
            from orders o
            left join accounts a on a.id_person = o.person_id
            left join order_attributions oa on oa.order_id = o.id
            where o.id = :orderId
            """;

    static final String ATTRIBUTE_SQL = """
            insert into ad_events (day, campaign_id, product_id, placement, kind, visitor_hash, order_id, amount)
            select :day, click.campaign_id, line.product_id, click.placement, 'ORDER', click.visitor_hash,
                   :orderId, line.amount
            from (select od.product_id, sum(od.units * od.unit_price) as amount
                  from order_details od
                  where od.order_id = :orderId
                  group by od.product_id) line
            join lateral (select e.campaign_id, e.placement, e.visitor_hash
                          from ad_events e
                          where e.product_id = line.product_id
                            and e.kind = 'CLICK'
                            and e.visitor_hash in (:visitors)
                            and e.created_at >= :since
                          order by e.created_at desc
                          limit 1) click on true
            where line.amount > 0
            on conflict do nothing
            """;

    private final JdbcClient jdbcClient;
    private final AdVisitors visitors;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public AdOrderAttribution(JdbcClient jdbcClient, AdVisitors visitors, PlatformTransactionManager transactionManager) {
        this(jdbcClient, visitors, requiresNew(transactionManager), Clock.systemUTC());
    }

    AdOrderAttribution(JdbcClient jdbcClient, AdVisitors visitors, TransactionTemplate transactionTemplate,
            Clock clock) {
        this.jdbcClient = jdbcClient;
        this.visitors = visitors;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderPaid(OrderPaid event) {
        try {
            Integer recorded = transactionTemplate.execute(status -> attribute(event.orderId()));
            if (recorded != null && recorded > 0) {
                log.info("Order {} attributed to {} ad event(s)", event.orderId(), recorded);
            }
        } catch (RuntimeException failure) {
            log.warn("Ad attribution failed for order {}: {}", event.orderId(), failure.getClass().getSimpleName());
        }
    }

    /** Inserta los eventos {@code ORDER} y devuelve cuántos. */
    int attribute(UUID orderId) {
        Set<String> hashes = new LinkedHashSet<>();
        List<VisitorRow> rows = jdbcClient.sql(VISITORS_SQL)
                .param("orderId", orderId)
                .query((rs, rowNum) -> new VisitorRow(rs.getObject("account_id", UUID.class),
                        rs.getObject("anonymous_id", UUID.class)))
                .list();
        for (VisitorRow row : rows) {
            if (row.accountId() != null) {
                hashes.add(visitors.ofAccount(row.accountId()));
            }
            if (row.anonymousId() != null) {
                String anonymous = visitors.ofAnonymous(row.anonymousId().toString());
                if (anonymous != null) {
                    hashes.add(anonymous);
                }
            }
        }
        if (hashes.isEmpty()) {
            return 0;
        }
        OffsetDateTime since = OffsetDateTime.ofInstant(clock.instant().minus(Duration.ofDays(WINDOW_DAYS)),
                ZoneOffset.UTC);
        return jdbcClient.sql(ATTRIBUTE_SQL)
                .param("day", AdText.today(clock))
                .param("orderId", orderId)
                .param("visitors", List.copyOf(hashes))
                .param("since", since)
                .update();
    }

    record VisitorRow(UUID accountId, UUID anonymousId) {
    }

    private static TransactionTemplate requiresNew(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
