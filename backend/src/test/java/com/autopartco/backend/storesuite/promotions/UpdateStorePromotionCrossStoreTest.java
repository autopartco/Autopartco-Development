package com.autopartco.backend.storesuite.promotions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.core.pricing.PromotionService;
import com.autopartco.backend.core.pricing.PromotionTargets;
import com.autopartco.backend.core.pricing.internal.PromotionRepository;
import com.autopartco.backend.shared.error.NotFoundException;

/** IDOR entre almacenes: con permiso en el almacén A no se puede modificar una promoción del almacén B. */
@ExtendWith(MockitoExtension.class)
class UpdateStorePromotionCrossStoreTest {
    private static final String EMAIL = "staff-a@example.com";
    private static final UUID STORE_A = UUID.randomUUID();
    private static final UUID STORE_B = UUID.randomUUID();
    private static final UUID PROMOTION_OF_B = UUID.randomUUID();

    @Mock
    private PlanEntitlements planEntitlements;
    @Mock
    private StoreAccess storeAccess;
    @Mock
    private StoreService storeService;
    @Mock
    private PromotionRepository promotionRepository;
    @Mock
    private PromotionTargets promotionTargets;

    private UpdateStorePromotion useCase() {
        return new UpdateStorePromotion(planEntitlements, new StorePromotionAccess(storeAccess, storeService),
                new PromotionService(promotionRepository, promotionTargets));
    }

    @Test
    void promotionOfAnotherStoreThroughOwnStoreIsNotFound() {
        when(storeService.exists(STORE_A)).thenReturn(true);
        when(promotionRepository.findStorePromotion(STORE_A, PROMOTION_OF_B)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> useCase().execute(STORE_A, PROMOTION_OF_B, EMAIL, null));

        assertEquals("PROMOTION_NOT_FOUND", exception.getCode());
        verify(storeAccess).requirePermission(EMAIL, STORE_A, StorePermissionCodes.PROMOTION_UPDATE);
        verify(promotionRepository, never()).save(any());
        verifyNoInteractions(promotionTargets);
    }

    @Test
    void anotherStoreWithoutPermissionIsForbiddenBeforeLookingUpThePromotion() {
        doThrow(new AccessDeniedException("Store permission denied: " + StorePermissionCodes.PROMOTION_UPDATE))
                .when(storeAccess).requirePermission(EMAIL, STORE_B, StorePermissionCodes.PROMOTION_UPDATE);

        assertThrows(AccessDeniedException.class, () -> useCase().execute(STORE_B, PROMOTION_OF_B, EMAIL, null));

        verifyNoInteractions(storeService, promotionRepository, promotionTargets);
    }
}
