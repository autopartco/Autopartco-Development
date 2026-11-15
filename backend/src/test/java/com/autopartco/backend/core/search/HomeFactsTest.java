package com.autopartco.backend.core.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

class HomeFactsTest {
    @SuppressWarnings("unchecked")
    @Test
    void statsAndCategoriesAreLoadedOnceUntilInvalidated() {
        AtomicInteger statsLoads = new AtomicInteger();
        AtomicInteger categoryLoads = new AtomicInteger();
        JdbcClient jdbc = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
        when(jdbc.sql(HomeFacts.STATS_SQL).query(any(RowMapper.class)).single()).thenAnswer(call -> {
            statsLoads.incrementAndGet();
            return new HomeFacts.Stats(1, 2, 3);
        });
        when(jdbc.sql(HomeFacts.CATEGORIES_SQL).param("limit", HomeFacts.MAX_CATEGORIES)
                .query(any(RowMapper.class)).list()).thenAnswer(call -> {
                    categoryLoads.incrementAndGet();
                    return List.of(new HomeFacts.CategoryCount(null, "FRENOS", 4));
                });
        HomeFacts facts = new HomeFacts(jdbc);

        assertThat(facts.stats()).isEqualTo(new HomeFacts.Stats(1, 2, 3));
        facts.stats();
        facts.categories();
        facts.categories();
        assertThat(statsLoads).hasValue(1);
        assertThat(categoryLoads).hasValue(1);

        facts.invalidate();
        facts.stats();
        facts.categories();
        assertThat(statsLoads).hasValue(2);
        assertThat(categoryLoads).hasValue(2);
    }
}
