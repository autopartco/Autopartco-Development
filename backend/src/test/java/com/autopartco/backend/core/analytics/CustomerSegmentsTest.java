package com.autopartco.backend.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.shared.error.BadRequestException;

class CustomerSegmentsTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    @Test
    void newCustomerHasOnePurchaseWithinThirtyDays() {
        assertThat(CustomerSegment.classify(1, 30, 30)).isEqualTo(CustomerSegment.NEW);
        assertThat(CustomerSegment.classify(1, 31, 31)).isEqualTo(CustomerSegment.OCCASIONAL);
        // Una compra en la ventana pero historial antiguo: no es nuevo.
        assertThat(CustomerSegment.classify(1, 400, 10)).isEqualTo(CustomerSegment.OCCASIONAL);
    }

    @Test
    void frequentNeedsThreePurchasesAndRecentLastOrder() {
        assertThat(CustomerSegment.classify(3, 200, 60)).isEqualTo(CustomerSegment.FREQUENT);
        assertThat(CustomerSegment.classify(3, 200, 61)).isEqualTo(CustomerSegment.AT_RISK);
        assertThat(CustomerSegment.classify(2, 200, 10)).isEqualTo(CustomerSegment.OCCASIONAL);
    }

    @Test
    void atRiskBetweenSixtyOneAndOneHundredEightyDays() {
        assertThat(CustomerSegment.classify(2, 300, 61)).isEqualTo(CustomerSegment.AT_RISK);
        assertThat(CustomerSegment.classify(2, 300, 180)).isEqualTo(CustomerSegment.AT_RISK);
        assertThat(CustomerSegment.classify(1, 300, 100)).isEqualTo(CustomerSegment.OCCASIONAL);
    }

    @Test
    void dormantAfterOneHundredEightyDaysRegardlessOfOrders() {
        assertThat(CustomerSegment.classify(5, 300, 181)).isEqualTo(CustomerSegment.DORMANT);
        assertThat(CustomerSegment.classify(0, 900, 500)).isEqualTo(CustomerSegment.DORMANT);
    }

    @Test
    void parseAcceptsExactCodesAndRejectsUnknown() {
        assertThat(CustomerSegment.parse(null)).isNull();
        assertThat(CustomerSegment.parse("at_risk")).isEqualTo(CustomerSegment.AT_RISK);
        assertThatThrownBy(() -> CustomerSegment.parse("VIP"))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("INVALID_SEGMENT");
        assertThat(List.of(CustomerSegment.values()).stream().map(Enum::name).toList())
                .containsExactly("NEW", "FREQUENT", "AT_RISK", "DORMANT", "OCCASIONAL");
    }

    @Test
    void storeReportAlwaysListsFiveSegmentsAndFiltersCustomers() {
        CustomerAnalyticsQueries queries = mock(CustomerAnalyticsQueries.class);
        UUID storeId = UUID.randomUUID();
        UUID frequent = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        when(queries.activity(eq(storeId), any())).thenReturn(List.of(
                activity(frequent, 4, "400000", 200, 5),
                activity(fresh, 1, "50000", 10, 10)));

        CustomerReport report = new CustomerSegments(queries).storeCustomers(storeId, null, TODAY);

        assertThat(report.windowDays()).isEqualTo(365);
        assertThat(report.segments()).extracting(CustomerReport.SegmentSummary::code)
                .containsExactly(CustomerSegment.values());
        assertThat(report.segments()).extracting(CustomerReport.SegmentSummary::label)
                .containsExactly("Nuevos", "Frecuentes", "En riesgo", "Dormidos", "Ocasionales");
        assertThat(report.segments().get(1).customers()).isEqualTo(1);
        assertThat(report.segments().get(1).revenue()).isEqualByComparingTo("400000");
        assertThat(report.segments().get(2).customers()).isZero();
        assertThat(report.customers()).extracting(CustomerReport.Customer::personId).containsExactly(frequent, fresh);
        assertThat(report.customers().getFirst().averageTicket()).isEqualByComparingTo("100000");
        assertThat(report.customers().getFirst().daysSinceLastOrder()).isEqualTo(5);

        CustomerReport onlyNew = new CustomerSegments(queries).storeCustomers(storeId, CustomerSegment.NEW, TODAY);
        assertThat(onlyNew.customers()).extracting(CustomerReport.Customer::personId).containsExactly(fresh);
        assertThat(onlyNew.segments()).hasSize(5);
    }

    @Test
    void platformReportPaginatesAndAddsConsents() {
        CustomerAnalyticsQueries queries = mock(CustomerAnalyticsQueries.class);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(queries.activity(isNull(), any())).thenReturn(List.of(
                activity(second, 1, "10", 400, 300),
                activity(first, 3, "90", 100, 3)));
        when(queries.contacts(anyList())).thenReturn(List.of(
                new CustomerAnalyticsQueries.Contact(second, "b@example.com", true, false)));

        PlatformCustomerReport report = new CustomerSegments(queries).platformCustomers(null, 2, 1, TODAY);

        assertThat(report.page().total()).isEqualTo(2);
        assertThat(report.page().page()).isEqualTo(2);
        assertThat(report.page().items()).singleElement().satisfies(customer -> {
            assertThat(customer.personId()).isEqualTo(second);
            assertThat(customer.email()).isEqualTo("b@example.com");
            assertThat(customer.marketingEmail()).isTrue();
            assertThat(customer.marketingWhatsapp()).isFalse();
            assertThat(customer.segment()).isEqualTo(CustomerSegment.DORMANT);
        });
    }

    private static CustomerAnalyticsQueries.Activity activity(UUID personId, long orders, String revenue,
                                                              int firstDaysAgo, int lastDaysAgo) {
        return new CustomerAnalyticsQueries.Activity(personId, "Cliente", orders, new BigDecimal(revenue), 1,
                at(TODAY.minusDays(firstDaysAgo)), at(TODAY.minusDays(lastDaysAgo)));
    }

    private static OffsetDateTime at(LocalDate day) {
        return day.atTime(12, 0).atZone(Analytics.ZONE).toOffsetDateTime();
    }
}
