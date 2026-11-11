package com.autopartco.backend.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class VisibilityRecorderTest {
    private final JdbcClient jdbcClient = mock(JdbcClient.class);
    private final JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
    private final Map<String, Object> params = new HashMap<>();
    private final VisibilityRecorder recorder =
            new VisibilityRecorder(jdbcClient, "sal", "https://www.autopartco.co");
    private final UUID store = UUID.randomUUID();
    private final UUID product = UUID.randomUUID();
    private final UUID branch = UUID.randomUUID();
    private final UUID service = UUID.randomUUID();

    private static VisibilityRecorder.Visit visit(String kind, UUID store, UUID product, String channel) {
        return new VisibilityRecorder.Visit("anon-1", kind, store, product, "wa", null, null, channel, null, null);
    }

    private VisibilityRecorder.Visit click(String kind, UUID branchId, UUID serviceId) {
        return new VisibilityRecorder.Visit("anon-1", kind, store, product, "wa", null, null, null, branchId,
                serviceId);
    }

    /** El INSERT devuelve {@code rows} filas y los parámetros enviados quedan en {@link #params}. */
    private void insertReturns(int rows) {
        when(jdbcClient.sql(VisibilityRecorder.INSERT_SQL)).thenReturn(statement);
        when(statement.param(anyString(), any())).thenAnswer(invocation -> {
            params.put(invocation.getArgument(0), invocation.getArgument(1));
            return statement;
        });
        when(statement.update()).thenReturn(rows);
    }

    @Test
    void ignoresInvalidEventsWithoutTouchingTheDatabase() {
        assertThat(recorder.record(visit("CLICK", store, null, null))).isFalse();
        assertThat(recorder.record(visit("PRODUCT_VIEW", store, null, null))).isFalse();
        assertThat(recorder.record(visit("PROFILE_VIEW", null, null, null))).isFalse();
        assertThat(recorder.record(new VisibilityRecorder.Visit(" ", "PROFILE_VIEW", store, null, null, null, null,
                null, null, null))).isFalse();
        assertThat(recorder.record(new VisibilityRecorder.Visit("x".repeat(101), "PROFILE_VIEW", store, null, null,
                null, null, null, null, null))).isFalse();
        verify(jdbcClient, never()).sql(anyString());
    }

    @Test
    void recordsWhenTheStoreAndProductMatch() {
        insertReturns(1);

        assertThat(recorder.record(visit("product_view", store, product, null))).isTrue();
    }

    @Test
    void productOfAnotherStoreInsertsNothing() {
        insertReturns(0);

        assertThat(recorder.record(visit("PRODUCT_VIEW", store, product, null))).isFalse();
    }

    @Test
    void clicksRequireTheBranchAndNeverTouchTheDatabaseWithoutIt() {
        assertThat(recorder.record(click("WHATSAPP_CLICK", null, null))).isFalse();
        assertThat(recorder.record(click("DIRECTIONS_CLICK", null, service))).isFalse();
        verify(jdbcClient, never()).sql(anyString());
    }

    @Test
    void whatsappClickKeepsBranchProductAndServiceAndUsesTheRefAsSource() {
        insertReturns(1);

        assertThat(recorder.record(click("whatsapp_click", branch, service))).isTrue();

        assertThat(params)
                .containsEntry("kind", "WHATSAPP_CLICK")
                .containsEntry("source", "WHATSAPP")
                .containsEntry("storeId", store)
                .containsEntry("productId", product)
                .containsEntry("branchId", branch)
                .containsEntry("serviceId", service);
        assertThat(params.get("visitor")).asString().hasSize(64).doesNotContain("anon-1");
    }

    @Test
    void directionsClickWithoutServiceIsRecorded() {
        insertReturns(1);

        assertThat(recorder.record(click("DIRECTIONS_CLICK", branch, null))).isTrue();
        assertThat(params).containsEntry("kind", "DIRECTIONS_CLICK").containsEntry("branchId", branch)
                .containsEntry("serviceId", null);
    }

    @Test
    void branchOfAnotherStoreInsertsNothing() {
        insertReturns(0);

        assertThat(recorder.record(click("WHATSAPP_CLICK", UUID.randomUUID(), null))).isFalse();
    }

    @Test
    void branchAndServiceAreDroppedForViewsAndShares() {
        insertReturns(1);

        assertThat(recorder.record(new VisibilityRecorder.Visit("anon-1", "PROFILE_VIEW", store, null, null, null,
                null, null, branch, service))).isTrue();
        assertThat(params).containsEntry("branchId", null).containsEntry("serviceId", null);
    }

    @Test
    void shareUsesChosenChannelAndVisitsUseRef() {
        VisibilityRecorder.Visit share = visit("SHARE", store, product, "tt");
        assertThat(VisibilityRecorder.source(VisibilityRecorder.Kind.SHARE, share, "autopartco.co"))
                .isEqualTo(TrafficSource.TIKTOK);
        assertThat(VisibilityRecorder.source(VisibilityRecorder.Kind.SHARE, visit("SHARE", store, null, "?"),
                "autopartco.co")).isEqualTo(TrafficSource.OTHER);
        assertThat(VisibilityRecorder.source(VisibilityRecorder.Kind.PROFILE_VIEW, share, "autopartco.co"))
                .isEqualTo(TrafficSource.WHATSAPP);
        assertThat(VisibilityRecorder.source(VisibilityRecorder.Kind.WHATSAPP_CLICK, share, "autopartco.co"))
                .isEqualTo(TrafficSource.WHATSAPP);
    }

    @Test
    void insertOnlyCountsActiveStoresOwnProductsBranchesAndServices() {
        assertThat(VisibilityRecorder.INSERT_SQL)
                .contains("ss.code = 'ACTIVE'")
                .contains("p.store_id = s.id")
                .contains("b.store_id = s.id")
                .contains("v.store_id = s.id")
                .doesNotContain("account");
    }
}
