package com.autopartco.backend.core.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.advertising.internal.AdCampaign;
import com.autopartco.backend.core.advertising.internal.AdCampaignRepository;
import com.autopartco.backend.core.advertising.internal.AdPlacementPrice;
import com.autopartco.backend.core.advertising.internal.AdPlacementPriceRepository;
import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.storesuite.advertising.StoreAds;

class AdCampaignServiceTest {
    /** 10:00 en Bogotá del 2026-11-11. */
    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);
    private static final UUID STORE = UUID.randomUUID();
    private static final UUID ACCOUNT = UUID.randomUUID();

    private final AdCampaignRepository campaignRepository = mock(AdCampaignRepository.class);
    private final AdPlacementPriceRepository priceRepository = mock(AdPlacementPriceRepository.class);
    private final ListingQueries listingQueries = mock(ListingQueries.class);
    private final AdCandidateCache cache = mock(AdCandidateCache.class);
    private final AdCampaigns campaigns = new AdCampaigns(campaignRepository, priceRepository, listingQueries, cache,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID productA = UUID.randomUUID();
    private final UUID productB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(priceRepository.findAllById(any())).thenAnswer(invocation -> {
            List<AdPlacementPrice> prices = new java.util.ArrayList<>();
            for (Object placement : (Iterable<?>) invocation.getArgument(0)) {
                AdPlacement value = (AdPlacement) placement;
                prices.add(price(value, switch (value) {
                    case SEARCH_TOP -> "15000";
                    case HOME_FEATURED -> "25000";
                    case CATEGORY_TOP -> "12000";
                    case PRODUCT_RELATED -> "8000";
                }, true));
            }
            return prices;
        });
        when(listingQueries.publishedProductIdsAmong(eq(STORE), anyCollection()))
                .thenReturn(Set.of(productA, productB));
        when(campaignRepository.save(any(AdCampaign.class))).thenAnswer(invocation -> {
            AdCampaign campaign = invocation.getArgument(0);
            if (campaign.getId() == null) {
                campaign.setId(UUID.randomUUID());
            }
            return campaign;
        });
    }

    private static AdPlacementPrice price(AdPlacement placement, String daily, boolean active) {
        return AdPlacementPrice.builder().placement(placement).dailyPrice(new BigDecimal(daily)).active(active).build();
    }

    private AdCampaignRequest request(List<UUID> products, List<AdPlacement> placements, LocalDate start,
            LocalDate end) {
        return new AdCampaignRequest("Frenos noviembre", products, placements, start, end,
                new AdTargeting(List.of("Pastillas de Freno"), null, null, null, List.of("11001")));
    }

    private AdCampaign stored(AdCampaignStatus status, LocalDate start, LocalDate end) {
        AdCampaign campaign = AdCampaign.builder()
                .id(UUID.randomUUID())
                .storeId(STORE)
                .name("c")
                .status(status)
                .startsOn(start)
                .endsOn(end)
                .totalPrice(new BigDecimal("150000.00"))
                .createdBy(ACCOUNT)
                .productIds(new LinkedHashSet<>(List.of(productA)))
                .build();
        campaign.setPlacementList(List.of(AdPlacement.SEARCH_TOP));
        when(campaignRepository.lockByIdAndStoreId(campaign.getId(), STORE)).thenReturn(Optional.of(campaign));
        when(campaignRepository.findByIdAndStoreId(campaign.getId(), STORE)).thenReturn(Optional.of(campaign));
        when(campaignRepository.lockById(campaign.getId())).thenReturn(Optional.of(campaign));
        return campaign;
    }

    @Test
    void priceIsDailyRateTimesDaysTimesPlacements() {
        // 10 días × (15 000 + 25 000).
        AdCampaignView view = campaigns.create(STORE, ACCOUNT, request(List.of(productA, productB, productA),
                List.of(AdPlacement.SEARCH_TOP, AdPlacement.HOME_FEATURED), TODAY, TODAY.plusDays(9)));

        assertThat(view.status()).isEqualTo(AdCampaignStatus.DRAFT);
        assertThat(view.totalPrice()).isEqualByComparingTo("400000.00");
        assertThat(view.days()).isEqualTo(10);
        assertThat(view.productIds()).containsExactly(productA, productB);
        assertThat(view.targeting().keywords()).containsExactly("pastillas de freno");
        assertThat(view.targeting().cityCodes()).containsExactly("11001");

        // Misma tarifa: tarifa × días × ubicaciones.
        assertThat(AdCampaignRules.totalPrice(List.of(AdPlacement.SEARCH_TOP), java.util.Map.of(
                AdPlacement.SEARCH_TOP, new BigDecimal("15000")), TODAY, TODAY.plusDays(29)))
                .isEqualByComparingTo("450000.00");
    }

    @Test
    void productOfAnotherStoreOrUnpublishedIsRejected() {
        UUID foreign = UUID.randomUUID();

        assertThatThrownBy(() -> campaigns.create(STORE, ACCOUNT, request(List.of(productA, foreign),
                List.of(AdPlacement.SEARCH_TOP), TODAY, TODAY.plusDays(1))))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("AD_PRODUCT_NOT_PUBLISHED");
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void moreThanTwentyProductsIsRejected() {
        List<UUID> many = IntStream.range(0, 21).mapToObj(i -> UUID.randomUUID()).toList();

        assertThatThrownBy(() -> campaigns.create(STORE, ACCOUNT, request(many, List.of(AdPlacement.SEARCH_TOP),
                TODAY, TODAY.plusDays(1))))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("AD_TOO_MANY_PRODUCTS");
    }

    @Test
    void invalidDatesAreRejected() {
        List<AdPlacement> top = List.of(AdPlacement.SEARCH_TOP);
        List<UUID> products = List.of(productA);
        for (LocalDate[] range : new LocalDate[][] {
                {TODAY.minusDays(1), TODAY.plusDays(3)},     // empieza ayer
                {TODAY.plusDays(61), TODAY.plusDays(62)},    // más de 60 días adelante
                {TODAY.plusDays(5), TODAY.plusDays(4)},      // fin antes del inicio
                {TODAY, TODAY.plusDays(90)}}) {              // 91 días
            assertThatThrownBy(() -> campaigns.create(STORE, ACCOUNT, request(products, top, range[0], range[1])))
                    .isInstanceOf(BadRequestException.class)
                    .extracting("code").isEqualTo("AD_INVALID_DATES");
        }
        // Límites válidos: hoy, 60 días adelante y 90 días de duración.
        assertThat(campaigns.create(STORE, ACCOUNT, request(products, top, TODAY.plusDays(60), TODAY.plusDays(149)))
                .days()).isEqualTo(90);
    }

    @Test
    void inactivePlacementIsRejected() {
        doReturn(List.of(price(AdPlacement.SEARCH_TOP, "15000", false))).when(priceRepository).findAllById(any());

        assertThatThrownBy(() -> campaigns.create(STORE, ACCOUNT, request(List.of(productA),
                List.of(AdPlacement.SEARCH_TOP), TODAY, TODAY)))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("AD_PLACEMENT_UNAVAILABLE");
    }

    @Test
    void updateAndDeleteOnlyInDraft() {
        AdCampaign active = stored(AdCampaignStatus.ACTIVE, TODAY, TODAY.plusDays(5));
        AdCampaignRequest change = request(List.of(productA), List.of(AdPlacement.CATEGORY_TOP), TODAY,
                TODAY.plusDays(1));

        assertThatThrownBy(() -> campaigns.update(STORE, active.getId(), change))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("AD_CAMPAIGN_INVALID_STATE");
        assertThatThrownBy(() -> campaigns.delete(STORE, active.getId()))
                .isInstanceOf(ConflictException.class);

        AdCampaign draft = stored(AdCampaignStatus.DRAFT, TODAY, TODAY.plusDays(5));
        AdCampaignView updated = campaigns.update(STORE, draft.getId(), change);
        assertThat(updated.placements()).containsExactly(AdPlacement.CATEGORY_TOP);
        assertThat(updated.totalPrice()).isEqualByComparingTo("24000.00");
    }

    @Test
    void campaignOfAnotherStoreIsNotFound() {
        AdCampaign campaign = stored(AdCampaignStatus.DRAFT, TODAY, TODAY.plusDays(1));
        UUID otherStore = UUID.randomUUID();
        when(campaignRepository.lockByIdAndStoreId(campaign.getId(), otherStore)).thenReturn(Optional.empty());
        when(campaignRepository.findByIdAndStoreId(campaign.getId(), otherStore)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> campaigns.get(otherStore, campaign.getId()))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("AD_CAMPAIGN_NOT_FOUND");
        assertThatThrownBy(() -> campaigns.pause(otherStore, campaign.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void pauseResumeAndCancelFollowTheStateMachine() {
        AdCampaign campaign = stored(AdCampaignStatus.ACTIVE, TODAY.minusDays(1), TODAY.plusDays(3));

        assertThat(campaigns.pause(STORE, campaign.getId()).status()).isEqualTo(AdCampaignStatus.PAUSED);
        assertThat(campaigns.resume(STORE, campaign.getId()).status()).isEqualTo(AdCampaignStatus.ACTIVE);
        assertThat(campaign.getEndsOn()).isEqualTo(TODAY.plusDays(3));
        assertThat(campaigns.cancel(STORE, campaign.getId()).status()).isEqualTo(AdCampaignStatus.CANCELLED);
        assertThatThrownBy(() -> campaigns.resume(STORE, campaign.getId())).isInstanceOf(ConflictException.class);

        AdCampaign future = stored(AdCampaignStatus.PAUSED, TODAY.plusDays(2), TODAY.plusDays(3));
        assertThat(campaigns.resume(STORE, future.getId()).status()).isEqualTo(AdCampaignStatus.SCHEDULED);
        AdCampaign over = stored(AdCampaignStatus.PAUSED, TODAY.minusDays(5), TODAY.minusDays(1));
        assertThat(campaigns.resume(STORE, over.getId()).status()).isEqualTo(AdCampaignStatus.FINISHED);
    }

    @Test
    void pausedByRecordsWhoPausedAndIsClearedWhenLeavingPaused() {
        AdCampaign campaign = stored(AdCampaignStatus.ACTIVE, TODAY.minusDays(1), TODAY.plusDays(3));

        AdCampaignView paused = campaigns.pause(STORE, campaign.getId());
        assertThat(paused.status()).isEqualTo(AdCampaignStatus.PAUSED);
        assertThat(paused.pausedBy()).isEqualTo(AdPausedBy.STORE);
        AdCampaignView resumed = campaigns.resume(STORE, campaign.getId());
        assertThat(resumed.status()).isEqualTo(AdCampaignStatus.ACTIVE);
        assertThat(resumed.pausedBy()).isNull();

        campaigns.pause(STORE, campaign.getId());
        assertThat(campaigns.cancel(STORE, campaign.getId()).pausedBy()).isNull();

        AdCampaign rejected = stored(AdCampaignStatus.ACTIVE, TODAY, TODAY.plusDays(3));
        campaigns.adminPause(rejected.getId());
        AdCampaignView view = campaigns.reject(rejected.getId(), "Motivo");
        assertThat(view.status()).isEqualTo(AdCampaignStatus.REJECTED);
        assertThat(view.pausedBy()).isNull();

        AdCampaign over = stored(AdCampaignStatus.ACTIVE, TODAY.minusDays(5), TODAY.minusDays(1));
        campaigns.pause(STORE, over.getId());
        AdCampaignView finished = campaigns.resume(STORE, over.getId());
        assertThat(finished.status()).isEqualTo(AdCampaignStatus.FINISHED);
        assertThat(finished.pausedBy()).isNull();
    }

    @Test
    void storeCannotResumeAnAdminPauseButTheAdminCan() {
        AdCampaign campaign = stored(AdCampaignStatus.ACTIVE, TODAY.minusDays(1), TODAY.plusDays(3));

        AdCampaignView paused = campaigns.adminPause(campaign.getId());
        assertThat(paused.status()).isEqualTo(AdCampaignStatus.PAUSED);
        assertThat(paused.pausedBy()).isEqualTo(AdPausedBy.ADMIN);

        assertThatThrownBy(() -> campaigns.resume(STORE, campaign.getId()))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("AD_CAMPAIGN_PAUSED_BY_ADMIN");
        assertThat(campaign.getStatus()).isEqualTo(AdCampaignStatus.PAUSED);
        assertThat(campaign.getPausedBy()).isEqualTo(AdPausedBy.ADMIN);
        // El almacén tampoco puede convertirla en una pausa propia.
        assertThatThrownBy(() -> campaigns.pause(STORE, campaign.getId())).isInstanceOf(ConflictException.class);

        AdCampaignView resumed = campaigns.adminResume(campaign.getId());
        assertThat(resumed.status()).isEqualTo(AdCampaignStatus.ACTIVE);
        assertThat(resumed.pausedBy()).isNull();
        assertThatThrownBy(() -> campaigns.adminResume(campaign.getId()))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("AD_CAMPAIGN_INVALID_STATE");
    }

    @Test
    void adminPauseTakesOverAStorePauseAndAdminResumesAnyPause() {
        AdCampaign campaign = stored(AdCampaignStatus.SCHEDULED, TODAY.plusDays(2), TODAY.plusDays(5));
        campaigns.pause(STORE, campaign.getId());

        assertThat(campaigns.adminPause(campaign.getId()).pausedBy()).isEqualTo(AdPausedBy.ADMIN);
        assertThatThrownBy(() -> campaigns.adminPause(campaign.getId())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> campaigns.resume(STORE, campaign.getId()))
                .extracting("code").isEqualTo("AD_CAMPAIGN_PAUSED_BY_ADMIN");

        AdCampaign storePaused = stored(AdCampaignStatus.ACTIVE, TODAY, TODAY.plusDays(5));
        campaigns.pause(STORE, storePaused.getId());
        assertThat(campaigns.adminResume(storePaused.getId()).status()).isEqualTo(AdCampaignStatus.ACTIVE);
        assertThat(campaigns.adminResume(campaign.getId()).status()).isEqualTo(AdCampaignStatus.SCHEDULED);
    }

    @Test
    void dateJobFinishesPausedCampaignsAndClearsPausedBy() {
        assertThat(AdCampaignJobs.FINISH_SQL)
                .contains("status = 'FINISHED'")
                .contains("paused_by = null")
                .contains("'PAUSED'");
        assertThat(AdCampaignJobs.ACTIVATE_SQL).doesNotContain("PAUSED");
    }

    @Test
    void adCheckoutUsesItsOwnWompiRedirect() {
        AdCampaign draft = stored(AdCampaignStatus.DRAFT, TODAY, TODAY.plusDays(9));
        com.autopartco.backend.core.advertising.internal.AdPaymentRepository paymentRepository =
                mock(com.autopartco.backend.core.advertising.internal.AdPaymentRepository.class);
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        com.autopartco.backend.core.payments.WompiClient wompiClient =
                mock(com.autopartco.backend.core.payments.WompiClient.class);
        when(wompiClient.checkoutUrl(any())).thenReturn("https://checkout.wompi.co/p/?x");
        com.autopartco.backend.core.payments.WompiProperties properties =
                new com.autopartco.backend.core.payments.WompiProperties(
                        com.autopartco.backend.core.payments.WompiEnvironment.SANDBOX, "pub_test_x", "prv_test_x",
                        "integrity", "events", "https://checkout.wompi.co/p/",
                        "https://autopartco.co/portal/pago/resultado");
        AdCheckout checkout = new AdCheckout(campaignRepository, paymentRepository, campaigns, properties,
                wompiClient, mock(com.autopartco.backend.core.payments.WompiEventRouter.class),
                mock(com.autopartco.backend.core.payments.PaymentSyncRateLimiter.class),
                "https://autopartco.co//portal/publicidad/pago", Clock.fixed(NOW, ZoneOffset.UTC));

        AdCheckoutResponse response = checkout.checkout(STORE, draft.getId(), ACCOUNT);

        org.mockito.ArgumentCaptor<com.autopartco.backend.core.payments.WompiCheckoutRequest> sent =
                org.mockito.ArgumentCaptor.forClass(com.autopartco.backend.core.payments.WompiCheckoutRequest.class);
        verify(wompiClient).checkoutUrl(sent.capture());
        assertThat(sent.getValue().redirectUrl()).isEqualTo("https://autopartco.co/portal/publicidad/pago");
        assertThat(sent.getValue().reference()).startsWith("ADS-").isEqualTo(response.reference());
        assertThat(AdCheckout.REDIRECT_URL_PROPERTY)
                .isEqualTo("${app.ads.redirect-url:${app.frontend-base-url:http://localhost:5173}"
                        + "/portal/publicidad/pago}");
    }

    @Test
    void adminRejectFlagsManualRefundOnlyWhenPaidAndNotStarted() {
        AdCampaign scheduled = stored(AdCampaignStatus.SCHEDULED, TODAY.plusDays(2), TODAY.plusDays(5));
        scheduled.setPaidAt(LocalDateTime.of(2026, 11, 10, 9, 0));
        AdCampaignView rejected = campaigns.reject(scheduled.getId(), "  Imagen engañosa ");
        assertThat(rejected.status()).isEqualTo(AdCampaignStatus.REJECTED);
        assertThat(rejected.rejectedReason()).isEqualTo("Imagen engañosa");
        assertThat(rejected.requiresManualRefund()).isTrue();

        AdCampaign running = stored(AdCampaignStatus.ACTIVE, TODAY.minusDays(1), TODAY.plusDays(5));
        running.setPaidAt(LocalDateTime.of(2026, 11, 9, 9, 0));
        assertThat(campaigns.reject(running.getId(), "Motivo").requiresManualRefund()).isFalse();

        AdCampaign draft = stored(AdCampaignStatus.DRAFT, TODAY.plusDays(2), TODAY.plusDays(5));
        assertThat(campaigns.reject(draft.getId(), "Motivo").requiresManualRefund()).isFalse();
        assertThatThrownBy(() -> campaigns.reject(draft.getId(), "otra vez")).isInstanceOf(ConflictException.class);
    }

    @Test
    void storeUseCaseRequiresActiveSubscription() {
        StoreAccess storeAccess = mock(StoreAccess.class);
        AccountLookup accountLookup = mock(AccountLookup.class);
        PlanEntitlements planEntitlements = mock(PlanEntitlements.class);
        when(planEntitlements.isSubscriptionActive(STORE)).thenReturn(false);
        StoreAds storeAds = new StoreAds(storeAccess, accountLookup, planEntitlements, campaigns,
                mock(AdCheckout.class), mock(AdStatsQueries.class));

        assertThatThrownBy(() -> storeAds.create(STORE, "owner@example.com", request(List.of(productA),
                List.of(AdPlacement.SEARCH_TOP), TODAY, TODAY)))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("SUBSCRIPTION_INACTIVE");
        verify(storeAccess).requirePermission("owner@example.com", STORE, "STORE_UPDATE");
        verify(accountLookup, never()).requireByEmail(anyString());
    }
}
