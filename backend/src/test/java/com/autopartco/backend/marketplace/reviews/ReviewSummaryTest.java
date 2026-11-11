package com.autopartco.backend.marketplace.reviews;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.marketplace.reviews.ReviewSummary.AspectStat;
import com.autopartco.backend.marketplace.reviews.ReviewSummary.AspectSummary;

class ReviewSummaryTest {
    private static final List<String> PRODUCT = ReviewKind.PRODUCT.aspects();

    private static AspectStat stat(String aspect, String average, long count) {
        return new AspectStat(aspect, new BigDecimal(average), count);
    }

    @Test
    void emptySummaryHasNoAverageAndFiveZeroBuckets() {
        ReviewSummary summary = ReviewSummary.of(Map.of(), List.of(), PRODUCT);

        assertThat(summary.average()).isNull();
        assertThat(summary.count()).isZero();
        assertThat(summary.distribution()).containsExactly(Map.entry("1", 0L), Map.entry("2", 0L),
                Map.entry("3", 0L), Map.entry("4", 0L), Map.entry("5", 0L));
        assertThat(summary.aspects()).isEmpty();
        assertThat(summary.highlights()).isEmpty();
    }

    @Test
    void averageIsRoundedToOneDecimalFromTheDistribution() {
        ReviewSummary summary = ReviewSummary.of(Map.of(5, 2L, 4, 1L), List.of(), PRODUCT);

        assertThat(summary.count()).isEqualTo(3);
        assertThat(summary.average()).isEqualByComparingTo("4.7");
        assertThat(summary.distribution()).containsEntry("5", 2L).containsEntry("4", 1L).containsEntry("1", 0L);
    }

    @Test
    void badgesNeedThreeRatingsAndAnAverageOfAtLeastFourPointThree() {
        ReviewSummary summary = ReviewSummary.of(Map.of(5, 3L), List.of(
                stat("QUALITY", "4.30", 3),      // justo en el umbral
                stat("FIT", "5.00", 2),          // muy bueno pero con solo 2 calificaciones
                stat("VALUE", "4.29", 10),       // debajo del umbral (se muestra 4,3 pero no da insignia)
                stat("INSTALL", "4.80", 4)), PRODUCT);

        assertThat(summary.highlights()).containsExactly("INSTALL", "QUALITY");
        assertThat(summary.aspects()).extracting(AspectSummary::aspect)
                .containsExactly("QUALITY", "FIT", "VALUE", "INSTALL");
        assertThat(summary.aspects()).filteredOn(a -> a.aspect().equals("VALUE"))
                .extracting(AspectSummary::average).first().satisfies(v -> assertThat(v).isEqualByComparingTo("4.3"));
    }

    @Test
    void atMostThreeHighlightsOrderedByAverage() {
        ReviewSummary summary = ReviewSummary.of(Map.of(5, 9L), List.of(
                stat("QUALITY", "4.5", 5),
                stat("FIT", "4.9", 5),
                stat("AS_DESCRIBED", "4.4", 5),
                stat("VALUE", "4.7", 5),
                stat("INSTALL", "4.6", 3)), PRODUCT);

        assertThat(summary.highlights()).containsExactly("FIT", "VALUE", "INSTALL");
    }

    @Test
    void unknownAspectsAreIgnored() {
        ReviewSummary summary = ReviewSummary.of(Map.of(4, 3L), List.of(stat("SERVICE", "5", 3)), PRODUCT);

        assertThat(summary.aspects()).isEmpty();
        assertThat(summary.highlights()).isEmpty();
    }

    @Test
    void storeSummaryUsesStoreAspects() {
        ReviewSummary summary = ReviewSummary.of(Map.of(5, 4L), List.of(stat("SPEED", "4.75", 4)),
                ReviewKind.STORE.aspects());

        assertThat(summary.aspects()).containsExactly(new AspectSummary("SPEED", new BigDecimal("4.8"), 4));
        assertThat(summary.highlights()).containsExactly("SPEED");
    }

    @Test
    void constantsMatchThePlan() {
        assertThat(ReviewSummary.BADGE_MIN_RATINGS).isEqualTo(3);
        assertThat(ReviewSummary.BADGE_MIN_AVERAGE).isEqualByComparingTo("4.3");
        assertThat(ReviewSummary.MAX_HIGHLIGHTS).isEqualTo(3);
    }
}
