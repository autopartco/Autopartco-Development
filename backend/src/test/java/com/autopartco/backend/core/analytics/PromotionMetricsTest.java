package com.autopartco.backend.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class PromotionMetricsTest {
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 11, 5, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime ENDS_LATER = NOW.plusDays(5);
    private static final OffsetDateTime ENDED = NOW.minusDays(1);

    private static PromotionMetrics.Raw raw(long exposed, long exposedBuyers, long controlSize, long controlBuyers) {
        return new PromotionMetrics.Raw(exposed, exposedBuyers, controlSize, controlBuyers,
                7, 9, new BigDecimal("280000"), 12, new BigDecimal("2800000"), 56);
    }

    @Test
    void liftComparesExposedConversionAgainstControl() {
        // 40 expuestos, 20 compraron (0.5); control 200, 50 compraron (0.25) -> lift 1.0
        PromotionResult result = PromotionMetrics.compute(raw(40, 20, 200, 50), false, ENDS_LATER, NOW);

        assertThat(result.exposures()).isEqualTo(40);
        assertThat(result.exposedConversion()).isEqualByComparingTo("0.5");
        assertThat(result.controlConversion()).isEqualByComparingTo("0.25");
        assertThat(result.lift()).isEqualByComparingTo("1");
        assertThat(result.status()).isEqualTo(PromotionResult.RUNNING);
        assertThat(result.buyers()).isEqualTo(7);
        assertThat(result.orders()).isEqualTo(9);
        assertThat(result.revenue()).isEqualByComparingTo("280000.00");
        assertThat(result.unitsSold()).isEqualTo(12);
    }

    @Test
    void baselineIsPerDayOverTwentyEightDays() {
        PromotionResult result = PromotionMetrics.compute(raw(40, 20, 200, 50), false, ENDS_LATER, NOW);

        assertThat(result.baseline().revenuePerDay()).isEqualByComparingTo("100000.00");
        assertThat(result.baseline().ordersPerDay()).isEqualByComparingTo("2.00");
    }

    @Test
    void negativeLiftWhenExposedConvertLess() {
        PromotionResult result = PromotionMetrics.compute(raw(30, 3, 100, 20), false, ENDS_LATER, NOW);

        assertThat(result.lift()).isEqualByComparingTo("-0.5");
    }

    @Test
    void twentyNineExposedIsInsufficientDataWithCountersAndNoLift() {
        PromotionResult result = PromotionMetrics.compute(raw(29, 10, 200, 50), false, ENDED, NOW);

        assertThat(result.status()).isEqualTo(PromotionResult.INSUFFICIENT_DATA);
        assertThat(result.lift()).isNull();
        assertThat(result.exposures()).isEqualTo(29);
        assertThat(result.orders()).isEqualTo(9);
    }

    @Test
    void thirtyExposedAreEnough() {
        PromotionResult result = PromotionMetrics.compute(raw(30, 10, 200, 50), false, ENDS_LATER, NOW);

        assertThat(result.status()).isEqualTo(PromotionResult.RUNNING);
        assertThat(result.lift()).isNotNull();
    }

    @Test
    void endedPromotionIsFinishedAndEndingExactlyNowToo() {
        assertThat(PromotionMetrics.compute(raw(40, 20, 200, 50), false, ENDED, NOW).status())
                .isEqualTo(PromotionResult.FINISHED);
        assertThat(PromotionMetrics.compute(raw(40, 20, 200, 50), false, NOW, NOW).status())
                .isEqualTo(PromotionResult.FINISHED);
        assertThat(PromotionMetrics.compute(raw(40, 20, 200, 50), false, ENDS_LATER, NOW).status())
                .isEqualTo(PromotionResult.RUNNING);
    }

    @Test
    void zeroControlConversionGivesNullLiftWithoutDividingByZero() {
        PromotionResult noBuyersInControl = PromotionMetrics.compute(raw(40, 20, 200, 0), false, ENDS_LATER, NOW);
        PromotionResult emptyControl = PromotionMetrics.compute(raw(40, 20, 0, 0), false, ENDS_LATER, NOW);

        assertThat(noBuyersInControl.lift()).isNull();
        assertThat(noBuyersInControl.controlConversion()).isEqualByComparingTo("0");
        assertThat(emptyControl.lift()).isNull();
        assertThat(emptyControl.controlConversion()).isNull();
        assertThat(PromotionMetrics.lift(null, new BigDecimal("0.2"))).isNull();
    }

    @Test
    void audienceAllHasNoExposuresNorControlButKeepsSalesAndBaseline() {
        PromotionResult result = PromotionMetrics.compute(raw(0, 0, 0, 0), true, ENDS_LATER, NOW);

        assertThat(result.exposures()).isZero();
        assertThat(result.status()).isEqualTo(PromotionResult.INSUFFICIENT_DATA);
        assertThat(result.lift()).isNull();
        assertThat(result.exposedConversion()).isNull();
        assertThat(result.controlConversion()).isNull();
        assertThat(result.orders()).isEqualTo(9);
        assertThat(result.revenue()).isEqualByComparingTo("280000.00");
        assertThat(result.baseline().ordersPerDay()).isEqualByComparingTo("2.00");
    }

    @Test
    void emptyBaselineIsZeroNotNull() {
        PromotionMetrics.Raw empty = new PromotionMetrics.Raw(0, 0, 0, 0, 0, 0, null, 0, null, 0);

        PromotionResult result = PromotionMetrics.compute(empty, false, ENDS_LATER, NOW);

        assertThat(result.revenue()).isEqualByComparingTo("0");
        assertThat(result.baseline().revenuePerDay()).isEqualByComparingTo("0");
        assertThat(result.baseline().ordersPerDay()).isEqualByComparingTo("0");
    }
}
