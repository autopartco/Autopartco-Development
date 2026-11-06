package com.autopartco.backend.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SegmentMembershipTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final UUID PERSON = UUID.randomUUID();
    private static final UUID STORE_A = UUID.randomUUID();
    private static final UUID STORE_B = UUID.randomUUID();
    private static final UUID STORE_C = UUID.randomUUID();
    private static final UUID STORE_D = UUID.randomUUID();
    private static final UUID STORE_E = UUID.randomUUID();

    private final FakeQueries queries = new FakeQueries();
    private final MutableClock clock = new MutableClock(TODAY.atTime(12, 0).atZone(Analytics.ZONE).toInstant());
    private final SegmentMembership membership = new SegmentMembership(queries, clock);

    @Test
    void classifiesPlatformAndEachStoreWithTheCustomerSegmentRules() {
        queries.rows = List.of(
                row(null, 6, 300, 5),       // plataforma: frecuente
                row(STORE_A, 3, 200, 10),   // frecuente
                row(STORE_B, 2, 300, 90),   // en riesgo
                row(STORE_C, 1, 10, 10),    // nuevo
                row(STORE_D, 0, 500, 200),  // dormido
                row(STORE_E, 2, 200, 10));  // ocasional: no se emite

        SegmentMembership.Memberships result = membership.memberships(PERSON);

        assertThat(result.platformSegments()).containsExactly("FREQUENT");
        assertThat(result.storeSegments()).isEqualTo(Map.of(
                STORE_A, Set.of("FREQUENT"),
                STORE_B, Set.of("AT_RISK"),
                STORE_C, Set.of("NEW"),
                STORE_D, Set.of("DORMANT")));
        assertThat(membership.platformSegments(PERSON)).containsExactly("FREQUENT");
        assertThat(membership.storeSegments(PERSON)).containsOnlyKeys(STORE_A, STORE_B, STORE_C, STORE_D);
    }

    @Test
    void usesOneQueryWithTheSegmentWindow() {
        queries.rows = List.of(row(null, 1, 5, 5));

        membership.memberships(PERSON);

        assertThat(queries.calls).containsExactly(PERSON);
        assertThat(queries.windowStart).isEqualTo(
                TODAY.minusDays(CustomerSegment.WINDOW_DAYS - 1L).atStartOfDay(Analytics.ZONE).toOffsetDateTime());
        assertThat(SegmentMembershipQueries.BUYER_ACTIVITY_SQL)
                .contains("GROUPING SETS ((b.store_id), ())")
                .contains("o.person_id = :personId")
                .contains("'CONFIRMED', 'PREPARING', 'SHIPPED', 'DELIVERED'");
    }

    @Test
    void buyerWithoutPurchasesHasNoSegments() {
        // La fila de plataforma llega con 0 compras y fechas nulas: no es «dormido».
        queries.rows = List.of(new SegmentMembershipQueries.Row(null, 0, null, null));

        SegmentMembership.Memberships result = membership.memberships(PERSON);

        assertThat(result.platformSegments()).isEmpty();
        assertThat(result.storeSegments()).isEmpty();
    }

    @Test
    void anonymousHasNoSegmentsAndDoesNotQuery() {
        assertThat(membership.memberships(null)).isEqualTo(SegmentMembership.Memberships.NONE);
        assertThat(queries.calls).isEmpty();
    }

    @Test
    void cachesPerPersonForTenMinutes() {
        queries.rows = List.of(row(null, 3, 100, 5));
        membership.memberships(PERSON);

        queries.rows = List.of(row(null, 3, 100, 90));
        clock.advance(Duration.ofMinutes(9).plusSeconds(59));
        assertThat(membership.platformSegments(PERSON)).containsExactly("FREQUENT");
        assertThat(queries.calls).hasSize(1);

        clock.advance(Duration.ofSeconds(1));
        assertThat(membership.platformSegments(PERSON)).containsExactly("AT_RISK");
        assertThat(queries.calls).hasSize(2);
    }

    @Test
    void cacheIsPerPerson() {
        UUID other = UUID.randomUUID();
        queries.rows = List.of(row(null, 3, 100, 5));
        membership.memberships(PERSON);
        membership.memberships(other);
        membership.memberships(PERSON);

        assertThat(queries.calls).containsExactly(PERSON, other);
        assertThat(membership.cachedPersons()).isEqualTo(2);
    }

    private static SegmentMembershipQueries.Row row(UUID storeId, long orders, long daysSinceFirst,
                                                    long daysSinceLast) {
        return new SegmentMembershipQueries.Row(storeId, orders, daysAgo(daysSinceFirst), daysAgo(daysSinceLast));
    }

    private static OffsetDateTime daysAgo(long days) {
        return TODAY.minusDays(days).atTime(10, 0).atZone(Analytics.ZONE).toOffsetDateTime()
                .withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static final class FakeQueries extends SegmentMembershipQueries {
        List<Row> rows = List.of();
        final List<UUID> calls = new ArrayList<>();
        OffsetDateTime windowStart;

        FakeQueries() {
            super(null);
        }

        @Override
        List<Row> buyerActivity(UUID personId, OffsetDateTime windowStart) {
            calls.add(personId);
            this.windowStart = windowStart;
            return rows;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
