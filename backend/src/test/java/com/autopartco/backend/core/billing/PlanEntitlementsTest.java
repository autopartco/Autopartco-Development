package com.autopartco.backend.core.billing;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.billing.PlanEntitlements.Capacity;
import com.autopartco.backend.core.billing.internal.Plan;
import com.autopartco.backend.core.billing.internal.StoreSubscription;
import com.autopartco.backend.core.billing.internal.StoreSubscriptionRepository;
import com.autopartco.backend.core.catalog.ProductCatalog;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.organization.StaffDirectory;
import com.autopartco.backend.core.organization.StoreApplicationStatus;
import com.autopartco.backend.shared.error.ConflictException;

@ExtendWith(MockitoExtension.class)
class PlanEntitlementsTest {
    private final UUID storeId = UUID.randomUUID();

    @Mock
    private StoreSubscriptionRepository subscriptionRepository;

    @Mock
    private PlanService planService;

    @Mock
    private BranchService branchService;

    @Mock
    private ProductCatalog productCatalog;

    @Mock
    private StaffDirectory staffDirectory;

    @InjectMocks
    private PlanEntitlements entitlements;

    private static Plan trialPlan() {
        return Plan.builder()
                .code("TRY")
                .name("Prueba gratuita")
                .monthlyPriceCop(0)
                .currency("COP")
                .features(List.of())
                .maxBranches(1)
                .maxProducts(100)
                .maxUsers(2)
                .capabilities(List.of("BASIC_ANALYTICS", "CSV_IMPORT"))
                .enabled(true)
                .build();
    }

    private void trialing(LocalDateTime trialEndsAt) {
        StoreSubscription subscription = StoreSubscription.builder()
                .id(UUID.randomUUID())
                .storeId(storeId)
                .status(StoreApplicationStatus.ACTIVE)
                .trialEndsAt(trialEndsAt)
                .build();
        when(subscriptionRepository.findByStoreId(storeId)).thenReturn(Optional.of(subscription));
        when(planService.trialPlan()).thenReturn(trialPlan());
    }

    @Test
    void limitReachedCarriesCapacityDetails() {
        trialing(LocalDateTime.now().plusDays(10));
        when(productCatalog.countInStore(storeId)).thenReturn(100L);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> entitlements.requireCapacity(storeId, Capacity.PRODUCTS, 1)
        );

        assertEquals("SELLER_PLAN_LIMIT_REACHED", error.getCode());
        assertEquals(
                Map.of("capacity", "PRODUCTS", "limit", 100, "used", 100L, "requested", 1L, "planCode", "TRY"),
                error.getDetails()
        );
    }

    @Test
    void requireCapacityAllowsUpToTheLimit() {
        trialing(LocalDateTime.now().plusDays(10));
        when(productCatalog.countInStore(storeId)).thenReturn(99L);

        assertDoesNotThrow(() -> entitlements.requireCapacity(storeId, Capacity.PRODUCTS, 1));
    }

    @Test
    void missingCapabilityCarriesCapabilityAndPlanCode() {
        trialing(LocalDateTime.now().plusDays(10));

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> entitlements.requireCapability(storeId, "PROMOTIONS")
        );

        assertEquals("SELLER_PLAN_FEATURE_UNAVAILABLE", error.getCode());
        assertEquals(Map.of("capability", "PROMOTIONS", "planCode", "TRY"), error.getDetails());
    }

    @Test
    void includedCapabilityIsAllowed() {
        trialing(LocalDateTime.now().plusDays(10));

        assertDoesNotThrow(() -> entitlements.requireCapability(storeId, "CSV_IMPORT"));
    }

    @Test
    void expiredSubscriptionCarriesExpirationInstant() {
        LocalDateTime trialEnd = LocalDateTime.now().minusDays(1);
        trialing(trialEnd);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> entitlements.requireSubscriptionActive(storeId)
        );

        assertEquals("SELLER_SUBSCRIPTION_EXPIRED", error.getCode());
        assertEquals(Map.of("expiredAt", trialEnd.toString()), error.getDetails());
    }
}
