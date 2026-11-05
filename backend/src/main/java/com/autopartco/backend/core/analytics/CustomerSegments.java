package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/** Segmentación de clientes de un almacén o de toda la plataforma ({@link CustomerSegment}). */
@Service
@AllArgsConstructor
public class CustomerSegments {
    static final int STORE_LIST_LIMIT = 200;
    public static final int MAX_PAGE_SIZE = 200;
    public static final int DEFAULT_PAGE_SIZE = 50;

    private final CustomerAnalyticsQueries queries;

    /** Clientes del almacén (solo sus compras); filtro de segmento opcional (afecta solo a la lista). */
    @Transactional(readOnly = true)
    public CustomerReport storeCustomers(UUID storeId, CustomerSegment segment) {
        return storeCustomers(storeId, segment, Analytics.today());
    }

    CustomerReport storeCustomers(UUID storeId, CustomerSegment segment, LocalDate today) {
        List<Classified> classified = classify(queries.activity(storeId, windowStart(today)), today);
        List<CustomerReport.Customer> customers = classified.stream()
                .filter(customer -> segment == null || customer.segment() == segment)
                .sorted(BY_REVENUE)
                .limit(STORE_LIST_LIMIT)
                .map(customer -> new CustomerReport.Customer(
                        customer.activity().personId(),
                        customer.activity().name(),
                        customer.activity().orders(),
                        Analytics.money(customer.activity().revenue()),
                        Analytics.ratio(customer.activity().revenue(), customer.activity().orders()),
                        customer.activity().firstOrderAt(),
                        customer.activity().lastOrderAt(),
                        customer.daysSinceLast(),
                        customer.segment()))
                .toList();
        return new CustomerReport(today, CustomerSegment.WINDOW_DAYS, summaries(classified), customers);
    }

    /** Clientes de la plataforma, paginados (página desde 1, tamaño hasta {@value #MAX_PAGE_SIZE}). */
    @Transactional(readOnly = true)
    public PlatformCustomerReport platformCustomers(CustomerSegment segment, int page, int size) {
        return platformCustomers(segment, page, size, Analytics.today());
    }

    PlatformCustomerReport platformCustomers(CustomerSegment segment, int page, int size, LocalDate today) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<Classified> classified = classify(queries.activity(null, windowStart(today)), today);
        List<Classified> filtered = classified.stream()
                .filter(customer -> segment == null || customer.segment() == segment)
                .sorted(BY_REVENUE)
                .toList();
        List<Classified> pageItems = filtered.stream()
                .skip((long) (safePage - 1) * safeSize)
                .limit(safeSize)
                .toList();
        Map<UUID, CustomerAnalyticsQueries.Contact> contacts = queries.contacts(
                        pageItems.stream().map(customer -> customer.activity().personId()).toList())
                .stream()
                .collect(Collectors.toMap(CustomerAnalyticsQueries.Contact::personId, Function.identity()));
        List<PlatformCustomerReport.Customer> items = pageItems.stream()
                .map(customer -> {
                    CustomerAnalyticsQueries.Activity activity = customer.activity();
                    CustomerAnalyticsQueries.Contact contact = contacts.get(activity.personId());
                    return new PlatformCustomerReport.Customer(
                            activity.personId(),
                            activity.name(),
                            contact == null ? null : contact.email(),
                            activity.orders(),
                            activity.stores(),
                            Analytics.money(activity.revenue()),
                            Analytics.ratio(activity.revenue(), activity.orders()),
                            activity.lastOrderAt(),
                            customer.daysSinceLast(),
                            customer.segment(),
                            contact != null && contact.marketingEmail(),
                            contact != null && contact.marketingWhatsapp());
                })
                .toList();
        return new PlatformCustomerReport(
                today,
                CustomerSegment.WINDOW_DAYS,
                summaries(classified),
                PageResponse.of(items, safePage, safeSize, filtered.size()));
    }

    private static final Comparator<Classified> BY_REVENUE = Comparator
            .comparing((Classified customer) -> customer.activity().revenue()).reversed()
            .thenComparing(customer -> customer.activity().lastOrderAt(),
                    Comparator.nullsLast(Comparator.reverseOrder()));

    private static java.time.OffsetDateTime windowStart(LocalDate today) {
        return Analytics.startOf(today.minusDays(CustomerSegment.WINDOW_DAYS - 1L));
    }

    static List<Classified> classify(List<CustomerAnalyticsQueries.Activity> activity, LocalDate today) {
        List<Classified> classified = new ArrayList<>(activity.size());
        for (CustomerAnalyticsQueries.Activity customer : activity) {
            long daysSinceFirst = daysSince(customer.firstOrderAt(), today);
            long daysSinceLast = daysSince(customer.lastOrderAt(), today);
            classified.add(new Classified(customer, daysSinceLast,
                    CustomerSegment.classify(customer.orders(), daysSinceFirst, daysSinceLast)));
        }
        return classified;
    }

    static List<CustomerReport.SegmentSummary> summaries(List<Classified> classified) {
        Map<CustomerSegment, long[]> counts = new EnumMap<>(CustomerSegment.class);
        Map<CustomerSegment, BigDecimal> revenue = new EnumMap<>(CustomerSegment.class);
        for (CustomerSegment segment : CustomerSegment.values()) {
            counts.put(segment, new long[1]);
            revenue.put(segment, BigDecimal.ZERO);
        }
        for (Classified customer : classified) {
            counts.get(customer.segment())[0]++;
            revenue.merge(customer.segment(), customer.activity().revenue(), BigDecimal::add);
        }
        List<CustomerReport.SegmentSummary> summaries = new ArrayList<>();
        for (CustomerSegment segment : CustomerSegment.values()) {
            summaries.add(new CustomerReport.SegmentSummary(segment, segment.label(), counts.get(segment)[0],
                    Analytics.money(revenue.get(segment))));
        }
        return summaries;
    }

    private static long daysSince(java.time.OffsetDateTime instant, LocalDate today) {
        if (instant == null) {
            return Long.MAX_VALUE;
        }
        LocalDate day = instant.atZoneSameInstant(Analytics.ZONE).toLocalDate();
        return Math.max(0, ChronoUnit.DAYS.between(day, today));
    }

    record Classified(CustomerAnalyticsQueries.Activity activity, long daysSinceLast, CustomerSegment segment) {
    }
}
