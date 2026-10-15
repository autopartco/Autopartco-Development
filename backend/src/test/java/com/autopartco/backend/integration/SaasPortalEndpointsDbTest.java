package com.autopartco.backend.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.billing.SubscriptionStatus;
import com.autopartco.backend.core.billing.SubscriptionView;
import com.autopartco.backend.core.billing.Subscriptions;
import com.autopartco.backend.core.organization.StoreProfileResponse;
import com.autopartco.backend.storesuite.store.GetStoreProfile;
import com.autopartco.backend.storesuite.store.ListMyStores;
import com.autopartco.backend.storesuite.store.MyStoreResponse;
import com.autopartco.backend.storesuite.subscription.GetSubscription;
import com.autopartco.backend.storesuite.subscription.SubscriptionResponse;
import com.autopartco.backend.storesuite.subscription.ListSubscriptionPayments;

/**
 * Prueba de los casos de uso nuevos contra la BD de prueba {@code autopartco_saas_test}, sembrada con
 * {@code seed.sql} (fuera del repo). Ejecutar con {@code DB_NAME=autopartco_saas_test} y
 * {@code ./mvnw test -Dtest=SaasPortalEndpointsDbTest}. No prueba la capa HTTP: el módulo no tiene MockMvc.
 * Solo corre con la variable de entorno {@code DB_NAME=autopartco_saas_test}: un {@code mvn install} normal la omite.
 */
@EnabledIfEnvironmentVariable(named = "DB_NAME", matches = "autopartco_saas_test")
@SpringBootTest
class SaasPortalEndpointsDbTest {
    private static final String OWNER = "owner@test.co";
    private static final UUID STORE = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID FOREIGN_STORE = UUID.fromString("c0000000-0000-4000-8000-000000000002");

    @Autowired
    private ListMyStores listMyStores;

    @Autowired
    private GetStoreProfile getStoreProfile;

    @Autowired
    private GetSubscription getSubscription;

    @Autowired
    private ListSubscriptionPayments listSubscriptionPayments;

    @Autowired
    private Subscriptions subscriptions;

    @Test
    void meStoresListsOnlyActiveMembershipsWithOwnerRole() {
        List<MyStoreResponse> stores = listMyStores.execute(OWNER);

        assertEquals(1, stores.size());
        MyStoreResponse store = stores.getFirst();
        assertEquals(STORE, store.storeId());
        assertEquals("Repuestos Test", store.businessName());
        assertTrue(store.isOwner());
        assertEquals(List.of("Propietario"), store.roleNames());
        assertNull(store.logoUrl());
        assertNull(store.subscription());
    }

    @Test
    void profileReturnsDescriptionFromConfiguration() {
        StoreProfileResponse profile = getStoreProfile.execute(STORE, OWNER);

        assertEquals("Repuestos Test", profile.businessName());
        assertEquals("Tienda de prueba", profile.description());
        assertEquals("ACTIVE", profile.state());
    }

    @Test
    void profileOfAForeignStoreIsForbidden() {
        assertThrows(AccessDeniedException.class, () -> getStoreProfile.execute(FOREIGN_STORE, OWNER));
    }

    @Test
    void subscriptionIsTrialingWithOneMonthWindow() {
        SubscriptionResponse view = getSubscription.execute(STORE, OWNER);

        assertEquals(SubscriptionStatus.TRIALING, view.status());
        assertEquals("TRY", view.plan().code());
        assertNull(view.paidThrough());
        assertTrue(view.daysRemaining() > 0);
    }

    @Test
    void subscriptionOfAForeignStoreIsForbidden() {
        assertThrows(AccessDeniedException.class, () -> getSubscription.execute(FOREIGN_STORE, OWNER));
    }

    @Test
    void paymentsStartEmpty() {
        assertTrue(listSubscriptionPayments.execute(STORE, OWNER).isEmpty());
    }

    @Test
    void startTrialIsIdempotentForTheSeededStore() {
        // La tienda sembrada ya tiene prueba: no debe reiniciarla ni devolver otra fecha.
        SubscriptionView before = subscriptions.view(STORE, java.time.LocalDateTime.now());
        assertFalse(before.trialEndsAt() == null);
        assertEquals(before.trialEndsAt(), subscriptions.startTrial(STORE, java.time.LocalDateTime.now()));
    }
}
