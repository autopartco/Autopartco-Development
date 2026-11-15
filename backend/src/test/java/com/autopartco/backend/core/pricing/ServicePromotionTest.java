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
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.pricing.internal.PromotionRepository;
import com.autopartco.backend.shared.error.BadRequestException;

/** Validación de promociones con destino {@code SERVICE} y {@code SERVICE_CATEGORY}. */
class ServicePromotionTest {
    private static final UUID STORE = UUID.randomUUID();
    private static final UUID OTHER_STORE = UUID.randomUUID();
    private static final UUID SERVICE = UUID.randomUUID();
    private static final UUID FOREIGN_SERVICE = UUID.randomUUID();

    private final PromotionRepository repository = mock(PromotionRepository.class);
    private final PromotionTargets targets = mock(PromotionTargets.class);
    private final PromotionService service = new PromotionService(repository, targets, event -> { });

    @BeforeEach
    void setUp() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(targets.findServiceStore(SERVICE)).thenReturn(Optional.of(STORE));
        when(targets.findServiceStore(FOREIGN_SERVICE)).thenReturn(Optional.of(OTHER_STORE));
        when(targets.serviceCategoryExists("AC")).thenReturn(true);
    }

    @Test
    void storeCanPromoteItsOwnService() {
        PromotionResponse response = service.createForStore(STORE,
                request(PromotionTargetType.SERVICE, SERVICE, null, null), false);

        assertThat(response.targetType()).isEqualTo(PromotionTargetType.SERVICE);
        assertThat(response.targetId()).isEqualTo(SERVICE);
        assertThat(response.targetKey()).isNull();
    }

    @Test
    void anotherStoresServiceIsRejected() {
        assertInvalid(() -> service.createForStore(STORE,
                request(PromotionTargetType.SERVICE, FOREIGN_SERVICE, null, null), false));
        assertInvalid(() -> service.createForStore(STORE,
                request(PromotionTargetType.SERVICE, UUID.randomUUID(), null, null), false));
        verify(repository, never()).save(any());
    }

    @Test
    void platformCannotPromoteASingleService() {
        assertInvalid(() -> service.createForPlatform(request(PromotionTargetType.SERVICE, SERVICE, null, null)));
    }

    @Test
    void categoryPromotionStoresTheNormalizedKeyWithoutTargetId() {
        PromotionResponse response = service.createForPlatform(
                request(PromotionTargetType.SERVICE_CATEGORY, null, " ac ", null));

        assertThat(response.targetKey()).isEqualTo("AC");
        assertThat(response.targetId()).isNull();
    }

    @Test
    void categoryPromotionNeedsAValidKeyAndNoId() {
        assertInvalid(() -> service.createForStore(STORE,
                request(PromotionTargetType.SERVICE_CATEGORY, null, "NOPE", null), false));
        assertInvalid(() -> service.createForStore(STORE,
                request(PromotionTargetType.SERVICE_CATEGORY, SERVICE, "AC", null), false));
        assertInvalid(() -> service.createForStore(STORE,
                request(PromotionTargetType.SERVICE_CATEGORY, null, null, null), false));
    }

    @Test
    void servicePromotionsOnlyTargetEveryone() {
        assertThatThrownBy(() -> service.createForStore(STORE,
                request(PromotionTargetType.SERVICE, SERVICE, null, PromotionAudience.STORE_FREQUENT), true))
                .isInstanceOfSatisfying(BadRequestException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_PROMOTION_AUDIENCE"));
        verify(repository, never()).save(any());
    }

    @Test
    void otherTargetsRejectACategoryKey() {
        assertInvalid(() -> service.createForStore(STORE,
                request(PromotionTargetType.STORE, null, "AC", null), false));
    }

    @Test
    void portalJsonCarriesTheCategoryKey() {
        PromotionUpsertRequest parsed = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build()
                .readValue("""
                        {"name":"Aire","targetType":"SERVICE_CATEGORY","targetKey":"AC","discountType":"PERCENTAGE",
                         "discountValue":10,"startsAt":"2026-11-01T00:00:00Z","endsAt":"2026-12-01T00:00:00Z"}
                        """, PromotionUpsertRequest.class);

        assertThat(parsed.targetKey()).isEqualTo("AC");
        assertThat(parsed.targetType()).isEqualTo(PromotionTargetType.SERVICE_CATEGORY);
    }

    private void assertInvalid(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BadRequestException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("INVALID_PROMOTION_TARGET"));
    }

    private static PromotionUpsertRequest request(PromotionTargetType type, UUID targetId, String key,
            PromotionAudience audience) {
        OffsetDateTime start = OffsetDateTime.parse("2026-11-01T00:00:00Z");
        return new PromotionUpsertRequest("Promo", type, targetId, null, null, null,
                PromotionDiscountType.PERCENTAGE, new BigDecimal("15"), start, start.plusDays(30), true, audience,
                key);
    }
}
