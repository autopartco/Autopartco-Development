package com.autopartco.backend.marketplace.home;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.organization.ServiceCategory;
import com.autopartco.backend.core.pricing.ServicePrice;
import com.autopartco.backend.core.search.HomeFacts;
import com.autopartco.backend.core.search.ServiceHit;
import com.autopartco.backend.core.search.ServiceSearchResult;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** La caché Redis del inicio guarda {@link HomeContent} en JSON: el bloque de servicios debe sobrevivir el viaje. */
class HomeContentCacheJsonTest {
    /** Igual que {@code ResilientCacheManager}. */
    private final JsonMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @Test
    void servicesBlockRoundTrips() {
        ServiceHit hit = new ServiceHit(UUID.randomUUID(), "Alineación", ServiceCategory.ALIGNMENT_BALANCING, "d",
                null, new BigDecimal("51000.00"), new BigDecimal("60000.00"), true, 45, true,
                new ServiceHit.Store(UUID.randomUUID(), "Taller", null, new BigDecimal("4.5"), "SERVICES"),
                new ServiceHit.Branch(UUID.randomUUID(), "Centro", "Bogotá", "Calle 1", new BigDecimal("4.6"),
                        new BigDecimal("-74.1"), true),
                new BigDecimal("2.3"),
                new ServicePrice.Applied(UUID.randomUUID(), "Promo", "-15%",
                        OffsetDateTime.parse("2026-12-01T00:00:00Z")));
        HomeContent content = new HomeContent(new HomeFacts.Stats(1, 2, 3), List.of("bujias"), List.of(), List.of(),
                List.of(), new HomeContent.Services(
                        List.of(new ServiceSearchResult.CategoryCount(ServiceCategory.AC, 4)), List.of(hit),
                        List.of(hit)));

        HomeContent back = json.readValue(json.writeValueAsString(content), HomeContent.class);

        assertThat(back.services().nearby()).singleElement().satisfies(read -> {
            assertThat(read.promotion().label()).isEqualTo("-15%");
            assertThat(read.priceFrom()).isEqualByComparingTo("51000");
            assertThat(read.branch().openNow()).isTrue();
        });
        assertThat(back.services().categories()).containsExactly(
                new ServiceSearchResult.CategoryCount(ServiceCategory.AC, 4));
    }

    @Test
    void entriesCachedBeforeTheServicesBlockStillRead() {
        String old = """
                {"stats":{"stores":1,"products":2,"cities":3},"popularSearches":[],"categories":[],
                 "nearby":[],"organicPool":[]}
                """;

        HomeContent back = json.readValue(old, HomeContent.class);

        assertThat(back.services()).isEqualTo(HomeContent.Services.EMPTY);
    }
}
