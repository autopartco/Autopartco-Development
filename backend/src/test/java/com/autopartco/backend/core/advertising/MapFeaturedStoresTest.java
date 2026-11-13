package com.autopartco.backend.core.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Sin BD aislada: SQL, parámetros, tope de recomendados y tokens. */
@SuppressWarnings({"unchecked", "rawtypes"})
class MapFeaturedStoresTest {
    private static final Instant NOW = Instant.parse("2026-11-13T15:00:00Z");

    private final JdbcClient jdbcClient = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
    private final JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class, RETURNS_DEEP_STUBS);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final AdTokenSigner signer = new AdTokenSigner(AdTokenSigner.deriveKey("secret"), clock);
    private final MapFeaturedStores featured = new MapFeaturedStores(jdbcClient, signer, clock);

    private List<Object[]> rows(int count) {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new Object[] {UUID.randomUUID(), UUID.randomUUID()});
        }
        return rows;
    }

    private void returning(List<Object[]> rows) throws Exception {
        when(jdbcClient.sql(anyString())).thenReturn(statement);
        when(statement.param(anyString(), any())).thenReturn(statement);
        ArgumentCaptor<RowMapper> mapper = ArgumentCaptor.forClass(RowMapper.class);
        when(statement.query(mapper.capture()).list()).thenAnswer(invocation -> {
            List<Object> mapped = new ArrayList<>();
            for (Object[] row : rows) {
                ResultSet rs = mock(ResultSet.class);
                when(rs.getObject("campaign_id", UUID.class)).thenReturn((UUID) row[0]);
                when(rs.getObject("store_id", UUID.class)).thenReturn((UUID) row[1]);
                mapped.add(mapper.getValue().mapRow(rs, 0));
            }
            return mapped;
        });
    }

    @Test
    void sqlTakesActiveCurrentMapCampaignsOfPublishedCandidateStores() throws Exception {
        returning(rows(1));
        UUID store = UUID.randomUUID();

        featured.pick(List.of(store));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcClient).sql(sql.capture());
        assertThat(sql.getValue()).contains("distinct on (c.store_id)", "c.status = 'ACTIVE'",
                "c.starts_on <= :today and c.ends_on >= :today", "'MAP_FEATURED' = any (c.placements)",
                "ss.code = 'ACTIVE'", "c.store_id in (:storeIds)");
        verify(statement).param("today", LocalDate.of(2026, 11, 13));
        verify(statement).param(eq("storeIds"), eq(List.of(store)));
    }

    @Test
    void atMostThreeStoresWithVerifiableProductlessTokens() throws Exception {
        List<Object[]> rows = rows(5);
        returning(rows);

        List<MapFeaturedPick> picks = featured.pick(List.of(UUID.randomUUID()));

        assertThat(picks).hasSize(3);
        assertThat(picks).extracting(MapFeaturedPick::storeId).doesNotHaveDuplicates();
        for (MapFeaturedPick pick : picks) {
            AdTokenSigner.Claims claims = signer.verify(pick.adImpressionToken()).orElseThrow();
            assertThat(claims.campaignId()).isEqualTo(pick.campaignId());
            assertThat(claims.productId()).isNull();
            assertThat(claims.placement()).isEqualTo(AdPlacement.MAP_FEATURED);
        }
    }

    @Test
    void noCandidatesSkipsTheQuery() {
        assertThat(featured.pick(List.of())).isEmpty();
        verifyNoInteractions(jdbcClient);
    }

    @Test
    void queryFailureReturnsNoRecommendations() {
        when(jdbcClient.sql(anyString())).thenThrow(new IllegalStateException("db down"));

        assertThat(featured.pick(List.of(UUID.randomUUID()))).isEmpty();
    }
}
