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
        List<BranchRow> byBranch
) {
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
