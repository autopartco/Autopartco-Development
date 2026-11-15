package com.autopartco.backend.core.analytics;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Visibilidad del almacén en un rango. «Visitantes» son visitantes diarios: el mismo visitante cuenta una vez por día
 * (el hash cambia cada día), así que la suma de varios días puede repetir personas. Los clics de WhatsApp y de «Cómo
 * llegar» no cuentan como visitas.
 *
 * @param whatsappClicks clics para cotizar por WhatsApp (no garantizan una conversación)
 * @param directionsClicks clics en «Cómo llegar»
 * @param bySource visitas (perfil + publicaciones), compartidos y clics de WhatsApp por origen, de más a menos visitas
 * @param topProducts hasta {@value VisibilityQueries#TOP_PRODUCTS} publicaciones con más visitas
 * @param daily un elemento por día del rango, con ceros si no hubo visitas
 * @param byBranch sucursales con al menos un clic, de más a menos clics de WhatsApp
 * @param serviceViews vistas del detalle de sus servicios
 * @param topServices hasta {@value VisibilityQueries#TOP_SERVICES} servicios con vistas, clics o citas en el rango
 * @param bookings citas solicitadas en el rango y su resultado
 */
public record VisibilityReport(
        LocalDate from,
        LocalDate to,
        long profileViews,
        long profileVisitors,
        long productViews,
        long productVisitors,
        long shares,
        List<SourceRow> bySource,
        List<ProductRow> topProducts,
        List<DayRow> daily,
        long whatsappClicks,
        long directionsClicks,
        List<BranchRow> byBranch,
        long serviceViews,
        List<ServiceRow> topServices,
        Bookings bookings
) {
    public VisibilityReport(LocalDate from, LocalDate to, long profileViews, long profileVisitors, long productViews,
            long productVisitors, long shares, List<SourceRow> bySource, List<ProductRow> topProducts,
            List<DayRow> daily, long whatsappClicks, long directionsClicks, List<BranchRow> byBranch) {
        this(from, to, profileViews, profileVisitors, productViews, productVisitors, shares, bySource, topProducts,
                daily, whatsappClicks, directionsClicks, byBranch, 0, List.of(), Bookings.NONE);
    }

    public record ServiceRow(UUID serviceId, String name, long views, long whatsappClicks, long directionsClicks,
                             long bookings) {
    }

    /** {@code confirmed} incluye las que ya se completaron o en las que el comprador no llegó. */
    public record Bookings(long requested, long confirmed, long completed, long noShow) {
        public static final Bookings NONE = new Bookings(0, 0, 0, 0);
    }

    public record SourceRow(TrafficSource source, long views, long visitors, long shares, long whatsappClicks) {
    }

    public record ProductRow(UUID productId, String name, long views, long visitors, long shares,
                             long whatsappClicks) {
    }

    public record DayRow(LocalDate day, long profileViews, long productViews, long whatsappClicks) {
    }

    public record BranchRow(UUID branchId, String name, long whatsappClicks, long directionsClicks) {
    }
}
