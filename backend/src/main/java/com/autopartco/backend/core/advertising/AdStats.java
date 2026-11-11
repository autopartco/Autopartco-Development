package com.autopartco.backend.core.advertising;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Estadísticas de una campaña ({@code GET …/campaigns/{id}/stats}, §2). Cifras estimadas: el token, el límite por IP
 * y la deduplicación mitigan pero no eliminan los bots.
 * <ul>
 *   <li>{@code impressions}: eventos de impresión (uno por visitante, día, producto y ubicación).</li>
 *   <li>{@code reach}: suma de visitantes distintos de cada día.</li>
 *   <li>{@code ctr} = clics ÷ impresiones (0..1, 4 decimales).</li>
 *   <li>{@code orders}/{@code revenue}: pedidos pagados con clic en el anuncio en los 7 días previos, por el mismo
 *       visitante o la misma cuenta; {@code revenue} suma las líneas de los productos anunciados.</li>
 *   <li>{@code cost}: parte prorrateada por día del precio pagado que cae en el rango (0 si no está pagada).</li>
 *   <li>{@code costPerClick} = costo ÷ clics; {@code roas} = ingresos ÷ costo (0 si el divisor es 0).</li>
 * </ul>
 */
public record AdStats(
        LocalDate from,
        LocalDate to,
        long impressions,
        long reach,
        long clicks,
        BigDecimal ctr,
        long whatsappClicks,
        long addToCart,
        long orders,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal costPerClick,
        BigDecimal roas,
        List<Daily> daily,
        List<ByPlacement> byPlacement,
        List<ByProduct> byProduct
) {
    public record Daily(LocalDate day, long impressions, long reach, long clicks, long whatsappClicks,
            long addToCart, long orders, BigDecimal revenue) {
    }

    public record ByPlacement(AdPlacement placement, long impressions, long clicks, BigDecimal ctr,
            long whatsappClicks, long addToCart, long orders, BigDecimal revenue) {
    }

    public record ByProduct(UUID productId, String productName, long impressions, long clicks, BigDecimal ctr,
            long whatsappClicks, long addToCart, long orders, BigDecimal revenue) {
    }
}
