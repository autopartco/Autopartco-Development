package com.autopartco.backend.core.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.pricing.internal.Promotion;
import com.autopartco.backend.core.pricing.internal.PromotionRepository;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;

class PromotionServiceAudienceTest {
    private static final UUID STORE_ID = UUID.randomUUID();

    private final PromotionRepository repository = mock(PromotionRepository.class);
    private final PromotionService service = new PromotionService(repository, mock(PromotionTargets.class));

    @Test
    void audienceDefaultsToAllAndIsReturned() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PromotionResponse response = service.createForStore(STORE_ID, storeRequest(null), false);

        assertThat(response.audience()).isEqualTo(PromotionAudience.ALL);
    }

    @Test
    void storeCanTargetItsOwnSegments() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PromotionResponse response = service.createForStore(STORE_ID,
                storeRequest(PromotionAudience.STORE_AT_RISK), false);

        assertThat(response.audience()).isEqualTo(PromotionAudience.STORE_AT_RISK);
    }

    @Test
    void storePlatformFrequentNeedsAdvancedReports() {
        assertThatThrownBy(() -> service.createForStore(STORE_ID,
                storeRequest(PromotionAudience.PLATFORM_FREQUENT), false))
                .isInstanceOfSatisfying(ConflictException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("SELLER_PLAN_FEATURE_UNAVAILABLE"));
        verify(repository, never()).save(any());

        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.createForStore(STORE_ID, storeRequest(PromotionAudience.PLATFORM_FREQUENT), true)
                .audience()).isEqualTo(PromotionAudience.PLATFORM_FREQUENT);
    }

    @Test
    void platformRejectsStoreSegments() {
        assertThatThrownBy(() -> service.createForPlatform(platformRequest(PromotionAudience.STORE_FREQUENT)))
                .isInstanceOfSatisfying(BadRequestException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_PROMOTION_AUDIENCE"));
        verify(repository, never()).save(any());
    }

    @Test
    void platformAcceptsPlatformFrequent() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.createForPlatform(platformRequest(PromotionAudience.PLATFORM_FREQUENT)).audience())
                .isEqualTo(PromotionAudience.PLATFORM_FREQUENT);
    }

    @Test
    void updateReplacesTheAudience() {
        UUID promotionId = UUID.randomUUID();
        Promotion existing = Promotion.builder().scope(PromotionScope.STORE).storeId(STORE_ID)
                .audience(PromotionAudience.STORE_FREQUENT).build();
        when(repository.findStorePromotion(STORE_ID, promotionId)).thenReturn(java.util.Optional.of(existing));

        PromotionResponse response = service.updateForStore(STORE_ID, promotionId, storeRequest(null), false);

        assertThat(response.audience()).isEqualTo(PromotionAudience.ALL);
        assertThat(existing.getAudience()).isEqualTo(PromotionAudience.ALL);
    }

    private static PromotionUpsertRequest storeRequest(PromotionAudience audience) {
        return new PromotionUpsertRequest("Clientes", PromotionTargetType.STORE, null, null, null, null,
                PromotionDiscountType.PERCENTAGE, new BigDecimal("10.00"), OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().plusDays(7), true, audience);
    }

    private static PromotionUpsertRequest platformRequest(PromotionAudience audience) {
        return new PromotionUpsertRequest("Plataforma", PromotionTargetType.ALL, null, null, null, null,
                PromotionDiscountType.PERCENTAGE, new BigDecimal("5.00"), OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().plusDays(7), true, audience);
    }
}
