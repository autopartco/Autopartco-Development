package com.autopartco.backend.core.advertising;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.advertising.internal.AdCampaign;
import com.autopartco.backend.core.advertising.internal.AdCampaignRepository;
import com.autopartco.backend.shared.error.BadRequestException;

/**
 * Lecturas de {@code ad_events} para {@link AdStats}: una consulta agregada por día, ubicación, producto y tipo, y otra
 * por día con el alcance (visitantes distintos con impresión) y los pedidos distintos. El cálculo es de
 * {@link AdStatsCalculator}.
 */
@Service
public class AdStatsQueries {
    public static final int MAX_RANGE_DAYS = 366;

    static final String ROWS_SQL = """
            select e.day, e.placement, e.product_id, p.name as product_name, e.kind,
                   count(*) as events, count(distinct e.order_id) as orders, coalesce(sum(e.amount), 0) as amount
            from ad_events e
            left join products p on p.id = e.product_id
            where e.campaign_id = :campaignId and e.day between :from and :to
            group by e.day, e.placement, e.product_id, p.name, e.kind
            """;

    static final String DAY_TOTALS_SQL = """
            select e.day,
                   count(distinct e.visitor_hash) filter (where e.kind = 'IMPRESSION') as reach,
                   count(distinct e.order_id) filter (where e.kind = 'ORDER') as orders
            from ad_events e
            where e.campaign_id = :campaignId and e.day between :from and :to
            group by e.day
            """;

    private final AdCampaignRepository campaignRepository;
    private final JdbcClient jdbcClient;
    private final Clock clock;

    @Autowired
    public AdStatsQueries(AdCampaignRepository campaignRepository, JdbcClient jdbcClient) {
        this(campaignRepository, jdbcClient, Clock.systemUTC());
    }

    AdStatsQueries(AdCampaignRepository campaignRepository, JdbcClient jdbcClient, Clock clock) {
        this.campaignRepository = campaignRepository;
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    /**
     * Estadísticas de la campaña del almacén entre {@code from} y {@code to} ({@code YYYY-MM-DD}, ambos incluidos).
     * Por defecto, del inicio de la campaña a hoy (o a su fin). 404 {@code AD_CAMPAIGN_NOT_FOUND} si no es del almacén;
     * 400 {@code INVALID_RANGE} si las fechas no son válidas o el rango supera {@value #MAX_RANGE_DAYS} días.
     */
    @Transactional(readOnly = true)
    public AdStats stats(UUID storeId, UUID campaignId, String from, String to) {
        AdCampaign campaign = campaignRepository.findByIdAndStoreId(campaignId, storeId)
                .orElseThrow(AdCampaigns::notFound);
        LocalDate today = AdText.today(clock);
        LocalDate defaultTo = campaign.getEndsOn().isBefore(today) ? campaign.getEndsOn() : today;
        if (defaultTo.isBefore(campaign.getStartsOn())) {
            defaultTo = campaign.getStartsOn();
        }
        LocalDate end = isBlank(to) ? defaultTo : parse(to);
        LocalDate start = isBlank(from) ? (campaign.getStartsOn().isAfter(end) ? end : campaign.getStartsOn())
                : parse(from);
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw invalidRange();
        }
        List<AdStatsCalculator.Row> rows = jdbcClient.sql(ROWS_SQL)
                .param("campaignId", campaignId)
                .param("from", start)
                .param("to", end)
                .query((rs, rowNum) -> new AdStatsCalculator.Row(
                        rs.getObject("day", LocalDate.class),
                        AdPlacement.parse(rs.getString("placement")).orElse(null),
                        rs.getObject("product_id", UUID.class),
                        rs.getString("product_name"),
                        AdEventKind.parse(rs.getString("kind")).orElse(null),
                        rs.getLong("events"),
                        rs.getLong("orders"),
                        rs.getBigDecimal("amount")))
                .list()
                .stream()
                .filter(row -> row.placement() != null && row.kind() != null)
                .toList();
        List<AdStatsCalculator.DayTotals> days = jdbcClient.sql(DAY_TOTALS_SQL)
                .param("campaignId", campaignId)
                .param("from", start)
                .param("to", end)
                .query((rs, rowNum) -> new AdStatsCalculator.DayTotals(
                        rs.getObject("day", LocalDate.class), rs.getLong("reach"), rs.getLong("orders")))
                .list();
        BigDecimal cost = AdStatsCalculator.proratedCost(campaign.getTotalPrice(), campaign.getPaidAt() != null,
                campaign.getStartsOn(), campaign.getEndsOn(), start, end);
        return AdStatsCalculator.compute(start, end, rows, days, cost);
    }

    private static LocalDate parse(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException invalid) {
            throw invalidRange();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static BadRequestException invalidRange() {
        return new BadRequestException("INVALID_RANGE",
                "Rango de fechas inválido (YYYY-MM-DD, máximo " + MAX_RANGE_DAYS + " días).");
    }
}
