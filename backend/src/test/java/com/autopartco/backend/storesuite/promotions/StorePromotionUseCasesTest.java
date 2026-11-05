package com.autopartco.backend.storesuite.promotions;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.billing.PlanResponse;
import com.autopartco.backend.core.billing.SubscriptionAccess;
import com.autopartco.backend.core.pricing.PromotionAudience;
import com.autopartco.backend.core.pricing.PromotionDiscountType;
import com.autopartco.backend.core.pricing.PromotionTargetType;
import com.autopartco.backend.core.pricing.PromotionUpsertRequest;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.core.pricing.PromotionService;
import com.autopartco.backend.shared.error.NotFoundException;

/** Orden de chequeos de las promociones del almacén: capacidad del plan antes del permiso al escribir. */
@ExtendWith(MockitoExtension.class)
class StorePromotionUseCasesTest {
    private static final String EMAIL = "staff@example.com";

    @Mock
    private StoreAccess storeAccess;

    @Mock
    private StoreService storeService;

    @Mock
    private PlanEntitlements planEntitlements;

    @Mock
    private PromotionService promotionService;

    private final UUID storeId = UUID.randomUUID();

    private StorePromotionAccess access() {
        return new StorePromotionAccess(storeAccess, storeService);
    }

    @Test
    void createChecksCapabilityBeforePermission() {
        when(storeService.exists(storeId)).thenReturn(true);

        new CreateStorePromotion(planEntitlements, access(), promotionService).execute(storeId, EMAIL, null);

        InOrder order = inOrder(planEntitlements, storeAccess, storeService, promotionService);
        order.verify(planEntitlements).requireCapability(storeId, "PROMOTIONS");
        order.verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.PROMOTION_CREATE);
        order.verify(storeService).exists(storeId);
        order.verify(promotionService).createForStore(storeId, null, false);
    }

    @Test
    void createWithoutCapabilityDoesNotCheckPermission() {
        doThrow(new IllegalStateException("plan"))
                .when(planEntitlements).requireCapability(any(), anyString());

        assertThrows(IllegalStateException.class, () ->
                new CreateStorePromotion(planEntitlements, access(), promotionService).execute(storeId, EMAIL, null));

        verifyNoInteractions(storeAccess, storeService, promotionService);
    }

    @Test
    void missingStoreAnswersPromotionNotFound() {
        when(storeService.exists(storeId)).thenReturn(false);

        assertThrows(NotFoundException.class, () ->
                new ListStorePromotions(access(), promotionService).execute(storeId, EMAIL));

        verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.PROMOTION_READ);
        verifyNoInteractions(promotionService);
    }

    @Test
    void deleteDoesNotRequireCapability() {
        UUID promotionId = UUID.randomUUID();
        when(storeService.exists(storeId)).thenReturn(true);

        new DeleteStorePromotion(access(), promotionService).execute(storeId, promotionId, EMAIL);

        verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.PROMOTION_DELETE);
        verify(promotionService).deleteForStore(storeId, promotionId);
        verifyNoInteractions(planEntitlements);
    }

    @Test
    void platformAudienceIsAllowedOnlyWithAdvancedReports() {
        when(storeService.exists(storeId)).thenReturn(true);
        PromotionUpsertRequest frequent = request(PromotionAudience.PLATFORM_FREQUENT);
        when(planEntitlements.accessForStore(storeId)).thenReturn(Optional.of(access(List.of("PROMOTIONS"))));

        new CreateStorePromotion(planEntitlements, access(), promotionService).execute(storeId, EMAIL, frequent);
        verify(promotionService).createForStore(storeId, frequent, false);

        when(planEntitlements.accessForStore(storeId))
                .thenReturn(Optional.of(access(List.of("PROMOTIONS", "ADVANCED_REPORTS"))));
        UUID promotionId = UUID.randomUUID();
        new UpdateStorePromotion(planEntitlements, access(), promotionService)
                .execute(storeId, promotionId, EMAIL, frequent);
        verify(promotionService).updateForStore(storeId, promotionId, frequent, true);
    }

    @Test
    void storeAudiencesDoNotLookUpThePlan() {
        when(storeService.exists(storeId)).thenReturn(true);
        PromotionUpsertRequest atRisk = request(PromotionAudience.STORE_AT_RISK);

        new CreateStorePromotion(planEntitlements, access(), promotionService).execute(storeId, EMAIL, atRisk);

        verify(promotionService).createForStore(storeId, atRisk, false);
        verify(planEntitlements, never()).accessForStore(any());
    }

    private static PromotionUpsertRequest request(PromotionAudience audience) {
        return new PromotionUpsertRequest("Frecuentes", PromotionTargetType.STORE, null, null, null, null,
                PromotionDiscountType.PERCENTAGE, new BigDecimal("10.00"), OffsetDateTime.now(),
                OffsetDateTime.now().plusDays(7), true, audience);
    }

    private SubscriptionAccess access(List<String> capabilities) {
        PlanResponse plan = new PlanResponse(UUID.randomUUID(), "PRO", "Pro", null, 0L, "COP", List.of(), false,
                true, null, null, null, capabilities);
        return new SubscriptionAccess(storeId, plan, plan, false, null, null, null);
    }
}
