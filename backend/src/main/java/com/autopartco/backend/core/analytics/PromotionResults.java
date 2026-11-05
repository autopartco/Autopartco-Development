package com.autopartco.backend.core.analytics;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resultados de una promoción (alcance, ventas atribuidas, línea base y efecto sobre la conversión frente a un grupo
 * de control). Solo agregados, sin identidades. No comprueba permisos: lo hacen los casos de uso de
 * {@code storesuite.promotions} y {@code operations.promotions}, que ya validaron que la promoción es del ámbito.
 */
@Service
public class PromotionResults {
    /** Compradores del ámbito activos en este periodo previo al inicio forman el grupo de control. */
    static final int CONTROL_LOOKBACK_DAYS = 365;

    private final PromotionResultQueries queries;
    private final Clock clock;

    @Autowired
    public PromotionResults(PromotionResultQueries queries) {
        this(queries, Clock.systemUTC());
    }

    PromotionResults(PromotionResultQueries queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    /**
     * @param storeId almacén dueño; {@code null} si la promoción es de plataforma (ámbito: toda la plataforma)
     * @param audience nombre de la audiencia de la promoción; {@code ALL} no registra exposiciones
     */
    @Transactional(readOnly = true)
    public PromotionResult results(
            UUID promotionId,
            UUID storeId,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String audience
    ) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime windowEnd = endsAt.isBefore(now) ? endsAt : now;
        PromotionResultQueries.Attributed attributed = queries.attributed(promotionId);
        PromotionResultQueries.BaselineTotals baseline = queries.baseline(
                storeId, startsAt, startsAt.minusDays(PromotionMetrics.BASELINE_DAYS));
        boolean audienceAll = audience == null || "ALL".equals(audience);
        PromotionResultQueries.Conversion conversion = audienceAll
                ? new PromotionResultQueries.Conversion(0, 0, 0, 0)
                : queries.conversion(promotionId, storeId, startsAt, windowEnd,
                        startsAt.minusDays(CONTROL_LOOKBACK_DAYS));
        return PromotionMetrics.compute(new PromotionMetrics.Raw(
                conversion.exposed(), conversion.exposedBuyers(), conversion.controlSize(), conversion.controlBuyers(),
                attributed.buyers(), attributed.orders(), attributed.revenue(), attributed.units(),
                baseline.revenue(), baseline.orders()), audienceAll, endsAt, now);
    }
}
