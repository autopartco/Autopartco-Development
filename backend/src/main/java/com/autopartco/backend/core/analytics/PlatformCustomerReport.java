package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.autopartco.backend.shared.web.PageResponse;

/**
 * Clientes de toda la plataforma segmentados (misma regla que {@link CustomerReport}), paginados por
 * {@code revenue} descendente. {@code stores} = almacenes distintos con compras en la ventana. {@code marketingEmail} y
 * {@code marketingWhatsapp} = consentimiento vigente de {@code person_consents_current}.
 */
public record PlatformCustomerReport(
        LocalDate asOf,
        int windowDays,
        List<CustomerReport.SegmentSummary> segments,
        PageResponse<Customer> page
) {
    public record Customer(
            UUID personId,
            String name,
            String email,
            long orders,
            long stores,
            BigDecimal revenue,
            BigDecimal averageTicket,
            OffsetDateTime lastOrderAt,
            long daysSinceLastOrder,
            CustomerSegment segment,
            boolean marketingEmail,
            boolean marketingWhatsapp
    ) {
    }
}
