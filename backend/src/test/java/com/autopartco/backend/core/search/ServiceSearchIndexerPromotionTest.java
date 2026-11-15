package com.autopartco.backend.core.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import com.autopartco.backend.core.pricing.PriceCalculator;
import com.autopartco.backend.core.pricing.PromotionAudience;
import com.autopartco.backend.core.pricing.PromotionDiscountType;
import com.autopartco.backend.core.pricing.PromotionRule;
import com.autopartco.backend.core.pricing.PromotionScope;
import com.autopartco.backend.core.pricing.PromotionTargetType;
import com.autopartco.backend.core.pricing.ServicePricing;
import com.autopartco.backend.core.pricing.internal.PromotionRepository;

/** Sin BD: el índice aplica el precio con promoción vigente y vuelve al base cuando no hay ninguna. */
class ServiceSearchIndexerPromotionTest {
    private static final UUID STORE = UUID.randomUUID();
    private static final UUID SERVICE = UUID.randomUUID();
    private static final UUID PLAIN = UUID.randomUUID();

    private final SearchFakeJdbc jdbc = new SearchFakeJdbc();
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);

    static ServicePricing pricing(List<PromotionRule> rules) {
        PriceCalculator calculator = new PriceCalculator(mock(PromotionRepository.class));
        ServicePricing pricing = mock(ServicePricing.class);
        when(pricing.current()).thenReturn(new ServicePricing.Rules(calculator, rules));
        when(pricing.cached()).thenReturn(new ServicePricing.Rules(calculator, rules));
        return pricing;
    }

    @Test
    void activePromotionLowersPriceFromAndMarksIt() {
        jdbc.rows(sql -> sql.startsWith("select id, store_id, category, price_from"), call -> List.of(
                Map.of("id", SERVICE, "store_id", STORE, "category", "ALIGNMENT_BALANCING",
                        "price_from", new BigDecimal("60000")),
                Map.of("id", PLAIN, "store_id", STORE, "category", "AC", "price_from", new BigDecimal("80000"))));
        ServiceSearchIndexer indexer = new ServiceSearchIndexer(jdbc.client, transactions, pricing(List.of(
                new PromotionRule(UUID.randomUUID(), PromotionScope.STORE, STORE, "Alineación",
                        PromotionTargetType.SERVICE, SERVICE, null, null, null, PromotionDiscountType.PERCENTAGE,
                        new BigDecimal("15"), PromotionAudience.ALL, null,
                        OffsetDateTime.parse("2026-12-01T00:00:00Z")))));

        indexer.reindexServices(List.of(SERVICE, PLAIN));

        List<SearchFakeJdbc.Call> applied = jdbc.callsMatching(sql -> sql.startsWith("update search_services"));
        assertThat(applied).hasSize(1);
        assertThat(applied.getFirst().sql()).contains("has_promotion = true");
        assertThat((BigDecimal) applied.getFirst().positional().get(0)).isEqualByComparingTo("51000");
        assertThat(applied.getFirst().positional().get(1)).isEqualTo(SERVICE);
    }

    @Test
    void withoutActivePromotionsTheUpsertKeepsTheBasePrice() {
        ServiceSearchIndexer indexer = new ServiceSearchIndexer(jdbc.client, transactions, pricing(List.of()));

        indexer.reindexServices(List.of(SERVICE));

        // El upsert escribe price_from = base y has_promotion = false: una promoción vencida desaparece al reindexar.
        assertThat(jdbc.callsMatching(sql -> sql.startsWith("insert into search_services")).getFirst().sql())
                .contains("ss.price_from, ss.price_from, false, ss.booking_enabled");
        assertThat(jdbc.callsMatching(sql -> sql.startsWith("update search_services"))).isEmpty();
        assertThat(jdbc.callsMatching(sql -> sql.startsWith("select id, store_id, category"))).isEmpty();
    }

    @Test
    void refreshReindexesPromotedAndNewlyTargetedServices() {
        jdbc.rows(sql -> sql.contains("where has_promotion"), call -> List.of(Map.of("service_id", SERVICE)));
        ServiceSearchIndexer indexer = new ServiceSearchIndexer(jdbc.client, transactions, pricing(List.of()));

        indexer.refreshPromotions();

        String candidates = jdbc.callsMatching(sql -> sql.contains("where has_promotion")).getFirst().sql();
        assertThat(candidates).contains("p.starts_at <= now() and p.ends_at > now()", "p.audience = 'ALL'",
                "p.target_type = 'SERVICE_CATEGORY' and p.target_key = ss.category");
        assertThat(jdbc.callsMatching(sql -> sql.startsWith("insert into search_services")).getFirst().positional())
                .containsExactly(SERVICE);
    }
}
