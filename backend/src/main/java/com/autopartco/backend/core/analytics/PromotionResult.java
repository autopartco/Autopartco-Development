package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;

/**
 * Resultado de una promoción: alcance, ventas atribuidas, línea base del ámbito y efecto sobre la conversión.
 * Solo agregados; nunca identidades de compradores. {@code lift} y las conversiones son fracciones (0.25 = 25 %) y
 * pueden ser {@code null} cuando no hay datos suficientes.
 *
 * @param exposures personas distintas a las que se mostró el precio de la promoción (0 con audiencia {@code ALL})
 * @param buyers personas distintas con líneas de pedido de la promoción en ventas válidas
 * @param orders pedidos distintos con esas líneas
 * @param revenue ingresos de esas líneas (precio por unidades menos descuento)
 * @param unitsSold unidades de esas líneas
 * @param status {@code RUNNING}, {@code FINISHED} o {@code INSUFFICIENT_DATA}
 */
public record PromotionResult(
        long exposures,
        long buyers,
        long orders,
        BigDecimal revenue,
        long unitsSold,
        Baseline baseline,
        BigDecimal lift,
        BigDecimal controlConversion,
        BigDecimal exposedConversion,
        String status
) {
    public static final String RUNNING = "RUNNING";
    public static final String FINISHED = "FINISHED";
    public static final String INSUFFICIENT_DATA = "INSUFFICIENT_DATA";

    /** Ingresos y pedidos por día del mismo ámbito (almacén o plataforma) en los 28 días previos al inicio. */
    public record Baseline(BigDecimal revenuePerDay, BigDecimal ordersPerDay) {
    }
}
