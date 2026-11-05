package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Clientes de un almacén segmentados. {@code orders}, {@code revenue} y {@code averageTicket} son de los últimos
 * {@code windowDays} días; {@code firstOrderAt}/{@code lastOrderAt} de todo el historial. Máximo 200 clientes,
 * por {@code revenue} descendente.
 */
public record CustomerReport(
        LocalDate asOf,
        int windowDays,
        List<SegmentSummary> segments,
        List<Customer> customers
) {
    public record SegmentSummary(CustomerSegment code, String label, long customers, BigDecimal revenue) {
    }

    public record Customer(
            UUID personId,
            String name,
            long orders,
            BigDecimal revenue,
            BigDecimal averageTicket,
            OffsetDateTime firstOrderAt,
            OffsetDateTime lastOrderAt,
            long daysSinceLastOrder,
            CustomerSegment segment
    ) {
    }
}
