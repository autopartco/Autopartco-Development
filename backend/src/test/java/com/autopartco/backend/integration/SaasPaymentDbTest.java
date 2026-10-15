package com.autopartco.backend.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.autopartco.backend.core.billing.SimulatedPaymentRequest;
import com.autopartco.backend.core.billing.SimulatedPaymentRequest.Outcome;
import com.autopartco.backend.core.billing.SubscriptionStatus;
import com.autopartco.backend.shared.error.PaymentRequiredException;
import com.autopartco.backend.storesuite.dashboard.DashboardResponse;
import com.autopartco.backend.storesuite.dashboard.StoreDashboardQuery;
import com.autopartco.backend.storesuite.subscription.ListSubscriptionPayments;
import com.autopartco.backend.storesuite.subscription.PaySubscriptionSimulated;

/**
 * Cobro simulado y dashboard contra {@code autopartco_saas_test}. MUTA la BD (crea un pago): volver a sembrar con
 * {@code database/seeds/portal-dev/seed_portal_dev.sql} antes de repetir. Ejecutar solo, tras
 * {@code SaasPortalEndpointsDbTest}: {@code DB_NAME=autopartco_saas_test ./mvnw test -Dtest=SaasPaymentDbTest}.
 * Solo corre con la variable de entorno {@code DB_NAME=autopartco_saas_test}: un {@code mvn install} normal la omite.
 */
@EnabledIfEnvironmentVariable(named = "DB_NAME", matches = "autopartco_saas_test")
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SaasPaymentDbTest {
    private static final String OWNER = "owner@test.co";
    private static final UUID STORE = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired
    private PaySubscriptionSimulated pay;

    @Autowired
    private ListSubscriptionPayments payments;

    @Autowired
    private StoreDashboardQuery dashboard;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID planId(String code) {
        return jdbc.queryForObject("select id from seller_plans where code = ?", UUID.class, code);
    }

    @Test
    @Order(1)
    void dashboardExecutesNativeQueriesOnAStoreWithoutListings() {
        DashboardResponse response = dashboard.execute(STORE, OWNER);

        assertEquals(SubscriptionStatus.TRIALING, response.subscriptionStatus());
        assertEquals(0, response.inventory().listings());
        assertEquals(0, response.inventory().outOfStock());
        assertEquals(0, response.inventory().lowStock());
    }

    @Test
    @Order(2)
    void rejectedPaymentChangesNothing() {
        UUID plan = planId("BUSINESS");
        assertThrows(PaymentRequiredException.class, () -> pay.execute(
                STORE, OWNER, new SimulatedPaymentRequest(plan, 1, Outcome.REJECTED, UUID.randomUUID())));
        assertTrue(payments.execute(STORE, OWNER).isEmpty());
    }

    @Test
    @Order(3)
    void approvedPaymentActivatesIsIdempotentAndWritesEvents() {
        UUID plan = planId("BUSINESS");
        UUID clientRequestId = UUID.randomUUID();
        SimulatedPaymentRequest request = new SimulatedPaymentRequest(plan, 3, Outcome.APPROVED, clientRequestId);

        PaySubscriptionSimulated.Outcome first = pay.execute(STORE, OWNER, request);
        PaySubscriptionSimulated.Outcome second = pay.execute(STORE, OWNER, request);

        assertTrue(first.created());
        assertFalse(second.created());
        assertEquals(first.body().payment().id(), second.body().payment().id());
        assertEquals("SIMULATED", first.body().payment().provider());
        assertEquals(449_700L, first.body().payment().amountCop());
        assertEquals(SubscriptionStatus.ACTIVE, first.body().subscription().status());
        assertEquals(1, payments.execute(STORE, OWNER).size());
        Integer approved = jdbc.queryForObject(
                "select count(*) from seller_subscription_events where store_id = ? and event_type = 'PAYMENT_APPROVED'",
                Integer.class, STORE);
        Integer planChanged = jdbc.queryForObject(
                "select count(*) from seller_subscription_events where store_id = ? and event_type = 'PLAN_CHANGED'",
                Integer.class, STORE);
        assertEquals(1, approved);
        assertEquals(1, planChanged);
    }
}
