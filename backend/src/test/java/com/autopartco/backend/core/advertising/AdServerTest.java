package com.autopartco.backend.core.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AdServerTest {
    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");
    private static final UUID CATEGORY = UUID.randomUUID();
    private static final UUID MAKE = UUID.randomUUID();
    private static final UUID MODEL = UUID.randomUUID();

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final AdCandidateSource source = mock(AdCandidateSource.class);
    private final AdTokenSigner signer = new AdTokenSigner(AdTokenSigner.deriveKey("k"), clock);
    private final InMemoryAdCandidateCache cache = new InMemoryAdCandidateCache();
    private final InMemoryAdFrequencyCap frequencyCap = new InMemoryAdFrequencyCap();

    private AdServer server(long seed) {
        Random random = new Random(seed);
        return new AdServer(cache, source, frequencyCap, signer, clock, () -> random);
    }

    private static AdCandidate candidate(UUID campaign, UUID product, String name, UUID category,
            AdTargeting targeting) {
        return new AdCandidate(campaign, UUID.randomUUID(), product, AdText.words(name), category, targeting);
    }

    private static AdTargeting keywords(String... keywords) {
        return new AdTargeting(List.of(keywords), List.of(), List.of(), List.of(), List.of());
    }

    private void given(AdPlacement placement, AdCandidate... candidates) {
        when(source.load(placement, LocalDate.of(2026, 11, 11))).thenReturn(List.of(candidates));
    }

    @Test
    void matchesKeywordCategoryVehicleAndProductName() {
        AdCandidate byKeyword = candidate(UUID.randomUUID(), UUID.randomUUID(), "Kit embrague", null,
                keywords("pastillas de freno"));
        AdCandidate byCategory = candidate(UUID.randomUUID(), UUID.randomUUID(), "Disco", CATEGORY, AdTargeting.NONE);
        AdCandidate byMake = candidate(UUID.randomUUID(), UUID.randomUUID(), "Filtro", null,
                new AdTargeting(List.of(), List.of(), List.of(MAKE), List.of(), List.of()));
        AdCandidate byName = candidate(UUID.randomUUID(), UUID.randomUUID(), "Pastillas de freno delanteras", null,
                AdTargeting.NONE);

        AdContext text = new AdContext("Pastillas de FRENO", null, null, null, null, Set.of(), null);
        assertThat(AdServer.matches(byKeyword, text, AdPlacement.SEARCH_TOP)).isTrue();
        assertThat(AdServer.matches(byName, text, AdPlacement.SEARCH_TOP)).isTrue();
        assertThat(AdServer.matches(byCategory, text, AdPlacement.SEARCH_TOP)).isFalse();
        assertThat(AdServer.matches(byMake, text, AdPlacement.SEARCH_TOP)).isFalse();

        AdContext category = new AdContext(null, CATEGORY, null, null, null, Set.of(), null);
        assertThat(AdServer.matches(byCategory, category, AdPlacement.SEARCH_TOP)).isTrue();
        assertThat(AdServer.matches(byKeyword, category, AdPlacement.SEARCH_TOP)).isFalse();

        AdContext vehicle = new AdContext("filtro aceite", null, MAKE, MODEL, null, Set.of(), null);
        assertThat(AdServer.matches(byMake, vehicle, AdPlacement.SEARCH_TOP)).isTrue();
    }

    @Test
    void searchTopNeedsSignalsAndCityTargetingFilters() {
        AdCandidate general = candidate(UUID.randomUUID(), UUID.randomUUID(), "Bujia", null, AdTargeting.NONE);
        AdCandidate bogota = candidate(UUID.randomUUID(), UUID.randomUUID(), "Bujia iridio", null,
                new AdTargeting(List.of(), List.of(), List.of(), List.of(), List.of("11001")));
        AdContext empty = new AdContext(null, null, null, null, null, Set.of(), null);

        assertThat(AdServer.matches(general, empty, AdPlacement.SEARCH_TOP)).isFalse();
        assertThat(AdServer.matches(general, empty, AdPlacement.HOME_FEATURED)).isTrue();
        assertThat(AdServer.matches(bogota, empty, AdPlacement.HOME_FEATURED)).isFalse();
        assertThat(AdServer.matches(bogota,
                new AdContext("bujia", null, null, null, "05001", Set.of(), null), AdPlacement.SEARCH_TOP)).isFalse();
        assertThat(AdServer.matches(bogota,
                new AdContext("bujia", null, null, null, "11001", Set.of(), null), AdPlacement.SEARCH_TOP)).isTrue();
    }

    @Test
    void excludesProductsAlreadyShownAndSignsEachPick() {
        UUID campaign = UUID.randomUUID();
        UUID shown = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        given(AdPlacement.SEARCH_TOP,
                candidate(campaign, shown, "Pastillas freno", null, AdTargeting.NONE),
                candidate(campaign, other, "Pastillas freno ceramicas", null, AdTargeting.NONE));

        List<AdPick> picks = server(1).pick(AdPlacement.SEARCH_TOP,
                new AdContext("pastillas", null, null, null, null, Set.of(shown), "v1"), 2);

        assertThat(picks).extracting(AdPick::productId).containsExactly(other);
        AdTokenSigner.Claims claims = signer.verify(picks.getFirst().adImpressionToken()).orElseThrow();
        assertThat(claims.campaignId()).isEqualTo(campaign);
        assertThat(claims.productId()).isEqualTo(other);
        assertThat(claims.placement()).isEqualTo(AdPlacement.SEARCH_TOP);
    }

    @Test
    void respectsLimitAndPlacementSlots() {
        List<AdCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            candidates.add(candidate(UUID.randomUUID(), UUID.randomUUID(), "Bateria", null, AdTargeting.NONE));
        }
        given(AdPlacement.SEARCH_TOP, candidates.toArray(AdCandidate[]::new));

        List<AdPick> picks = server(2).pick(AdPlacement.SEARCH_TOP,
                new AdContext("bateria", null, null, null, null, Set.of(), "v1"), 10);

        assertThat(picks).hasSize(AdPlacement.SEARCH_TOP.slots());
        assertThat(picks).extracting(AdPick::campaignId).doesNotHaveDuplicates();
    }

    @Test
    void frequencyCapStopsTheSameCampaignAfterThreeViewsPerHour() {
        UUID campaign = UUID.randomUUID();
        given(AdPlacement.SEARCH_TOP, candidate(campaign, UUID.randomUUID(), "Amortiguador", null, AdTargeting.NONE));
        AdServer server = server(3);
        AdContext visitor = new AdContext("amortiguador", null, null, null, null, Set.of(), "visitor-1");

        for (int i = 0; i < AdFrequencyCap.MAX_PER_HOUR; i++) {
            assertThat(server.pick(AdPlacement.SEARCH_TOP, visitor, 2)).hasSize(1);
        }
        assertThat(server.pick(AdPlacement.SEARCH_TOP, visitor, 2)).isEmpty();
        // Otro visitante sí lo ve; y en otra ubicación el tope es aparte.
        assertThat(server.pick(AdPlacement.SEARCH_TOP,
                new AdContext("amortiguador", null, null, null, null, Set.of(), "visitor-2"), 2)).hasSize(1);
        // La caché de 60 s evita volver a cargar candidatos en cada petición.
        verify(source, times(1)).load(any(), any());
    }

    @Test
    void rotatesCampaignsUniformly() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        given(AdPlacement.SEARCH_TOP,
                candidate(first, UUID.randomUUID(), "Correa", null, AdTargeting.NONE),
                candidate(second, UUID.randomUUID(), "Correa", null, AdTargeting.NONE),
                candidate(third, UUID.randomUUID(), "Correa", null, AdTargeting.NONE));
        AdServer server = server(4);
        Map<UUID, Integer> shown = new HashMap<>();

        for (int i = 0; i < 3_000; i++) {
            // Sin visitante: sin tope de frecuencia.
            for (AdPick pick : server.pick(AdPlacement.SEARCH_TOP,
                    new AdContext("correa", null, null, null, null, Set.of(), null), 1)) {
                shown.merge(pick.campaignId(), 1, Integer::sum);
            }
        }

        assertThat(shown).containsOnlyKeys(first, second, third);
        shown.values().forEach(count -> assertThat(count).isBetween(850, 1150));
    }

    @Test
    void noCandidatesOrFailingSourceGiveNoAds() {
        when(source.load(any(), any())).thenThrow(new IllegalStateException("db down"));

        assertThat(server(5).pick(AdPlacement.SEARCH_TOP,
                new AdContext("correa", null, null, null, null, Set.of(), "v"), 2)).isEmpty();
    }

    @Test
    void scopesMatchSubcategoriesVehiclesAndSkipTheViewedStore() {
        UUID sub = UUID.randomUUID();
        UUID store = UUID.randomUUID();
        AdCandidate inSubcategory = candidate(UUID.randomUUID(), UUID.randomUUID(), "Disco", sub, AdTargeting.NONE);
        AdCandidate targetsSub = candidate(UUID.randomUUID(), UUID.randomUUID(), "Filtro", null,
                new AdTargeting(List.of(), List.of(sub), List.of(), List.of(), List.of()));
        AdCandidate byModel = candidate(UUID.randomUUID(), UUID.randomUUID(), "Amortiguador", null,
                new AdTargeting(List.of(), List.of(), List.of(), List.of(MODEL), List.of()));
        AdCandidate other = candidate(UUID.randomUUID(), UUID.randomUUID(), "Radiador", UUID.randomUUID(),
                AdTargeting.NONE);

        AdContext category = new AdContext(null, CATEGORY, null, null, null, Set.of(), null, Set.of(sub), null, null,
                null);
        assertThat(AdServer.matches(inSubcategory, category, AdPlacement.CATEGORY_TOP)).isTrue();
        assertThat(AdServer.matches(targetsSub, category, AdPlacement.CATEGORY_TOP)).isTrue();
        assertThat(AdServer.matches(other, category, AdPlacement.CATEGORY_TOP)).isFalse();

        AdContext vehicles = new AdContext(null, null, null, null, null, Set.of(), null, null, Set.of(MAKE),
                Set.of(MODEL), null);
        assertThat(AdServer.matches(byModel, vehicles, AdPlacement.PRODUCT_RELATED)).isTrue();
        assertThat(AdServer.matches(other, vehicles, AdPlacement.PRODUCT_RELATED)).isFalse();

        AdCandidate sameStore = new AdCandidate(UUID.randomUUID(), store, UUID.randomUUID(), List.of(), sub,
                AdTargeting.NONE);
        given(AdPlacement.CATEGORY_TOP, sameStore, inSubcategory);
        AdContext viewing = new AdContext(null, CATEGORY, null, null, null, Set.of(), null, Set.of(sub), null, null,
                store);
        assertThat(server(1).pick(AdPlacement.CATEGORY_TOP, viewing, 3)).extracting(AdPick::productId)
                .containsExactly(inSubcategory.productId());
    }
}
