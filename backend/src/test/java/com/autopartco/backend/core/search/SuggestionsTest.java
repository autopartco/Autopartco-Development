package com.autopartco.backend.core.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SuggestionsTest {
    @Test
    void popularQueriesNeedFiveSessionsOnTwoDistinctDays() {
        assertThat(Suggestions.MIN_QUERY_SESSIONS).isEqualTo(5);
        assertThat(Suggestions.MIN_QUERY_DAYS).isEqualTo(2);
        String sql = Suggestions.RELOAD_SQL.replaceAll("\\s+", " ");
        assertThat(sql)
                .contains("having count(distinct ms.session_hash) >= 5")
                .contains("and count(distinct (ms.occurred_at at time zone 'America/Bogota')::date) >= 2")
                .doesNotContain(">= 3")
                .doesNotContain("%d");
    }
}
