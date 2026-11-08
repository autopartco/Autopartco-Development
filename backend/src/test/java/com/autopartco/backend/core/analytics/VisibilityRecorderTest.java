package com.autopartco.backend.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class VisibilityRecorderTest {
    private final JdbcClient jdbcClient = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
    private final VisibilityRecorder recorder =
            new VisibilityRecorder(jdbcClient, "sal", "https://www.autopartco.co");
    private final UUID store = UUID.randomUUID();
    private final UUID product = UUID.randomUUID();

    private static VisibilityRecorder.Visit visit(String kind, UUID store, UUID product, String channel) {
        return new VisibilityRecorder.Visit("anon-1", kind, store, product, "wa", null, null, channel);
    }

    @Test
    void ignoresInvalidEventsWithoutTouchingTheDatabase() {
        assertThat(recorder.record(visit("CLICK", store, null, null))).isFalse();
        assertThat(recorder.record(visit("PRODUCT_VIEW", store, null, null))).isFalse();
        assertThat(recorder.record(visit("PROFILE_VIEW", null, null, null))).isFalse();
        assertThat(recorder.record(new VisibilityRecorder.Visit(" ", "PROFILE_VIEW", store, null, null, null, null,
                null))).isFalse();
        assertThat(recorder.record(new VisibilityRecorder.Visit("x".repeat(101), "PROFILE_VIEW", store, null, null,
                null, null, null))).isFalse();
        verify(jdbcClient, never()).sql(anyString());
    }

    @Test
    void recordsWhenTheStoreAndProductMatch() {
        when(jdbcClient.sql(VisibilityRecorder.INSERT_SQL).param(anyString(), any()).param(anyString(), any())
                .param(anyString(), any()).param(anyString(), any()).param(anyString(), any())
                .param(anyString(), any()).update()).thenReturn(1);

        assertThat(recorder.record(visit("product_view", store, product, null))).isTrue();
    }

    @Test
    void productOfAnotherStoreInsertsNothing() {
        when(jdbcClient.sql(VisibilityRecorder.INSERT_SQL).param(anyString(), any()).param(anyString(), any())
                .param(anyString(), any()).param(anyString(), any()).param(anyString(), any())
                .param(anyString(), any()).update()).thenReturn(0);

        assertThat(recorder.record(visit("PRODUCT_VIEW", store, product, null))).isFalse();
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
    }

    @Test
    void insertOnlyCountsActiveStoresAndOwnProducts() {
        assertThat(VisibilityRecorder.INSERT_SQL)
                .contains("ss.code = 'ACTIVE'")
                .contains("p.store_id = s.id")
                .doesNotContain("account");
    }
}
