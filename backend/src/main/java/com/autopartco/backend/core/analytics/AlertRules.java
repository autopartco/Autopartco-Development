package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reglas puras de las alertas del almacén: dada la «foto» de un almacén ({@link Snapshot}) devuelven las alertas
 * candidatas ({@link Candidate}) con mensajes en español de Colombia. No tocan la BD ni envían nada.
 *
 * <p>Reglas por defecto (umbral sobreescribible con {@link Thresholds}):</p>
 * <ul>
 *   <li>{@code SALES_DROP}: ventas de los 7 días completos anteriores a hoy menores que 65 % de la media semanal
 *       de las 8 semanas previas; exige historial de ventas de al menos 8 semanas antes de esa ventana.
 *       {@code WARNING}; {@code CRITICAL} si la caída llega a 40 % (proporcional si se cambia el umbral).</li>
 *   <li>{@code STOCKOUT_SOON}: cobertura menor de 7 días en producto clase A o B con stock. {@code WARNING}.</li>
 *   <li>{@code STOCKOUT}: producto publicado con stock 0 y ventas en los últimos 30 días. {@code CRITICAL}.</li>
 *   <li>{@code CUSTOMER_AT_RISK}: cliente {@code FREQUENT} hace 7 días y {@code AT_RISK} hoy. {@code INFO}.</li>
 *   <li>{@code DEAD_STOCK}: productos con stock sin ventas en 90 días (y con al menos esa antigüedad); un resumen
 *       por semana con el conteo y hasta {@value #DEAD_STOCK_PAYLOAD_LIMIT} productos. {@code INFO}.</li>
 * </ul>
 * <p>{@code dedupeKey = TIPO:objeto:semanaISO} (objeto: producto, cliente o, en {@code SALES_DROP} y
 * {@code DEAD_STOCK}, el almacén). El payload nunca lleva datos de contacto.</p>
 */
public final class AlertRules {
    public static final int SALES_WINDOW_DAYS = 7;
    public static final int BASELINE_WEEKS = 8;
    public static final int STOCKOUT_LOOKBACK_DAYS = 30;
    public static final int DEAD_STOCK_PAYLOAD_LIMIT = 20;
    /** Días de comparación entre la «foto» actual y la anterior de los clientes. */
    public static final int CUSTOMER_COMPARISON_DAYS = 7;

    private static final BigDecimal CRITICAL_SHARE = new BigDecimal("40").divide(new BigDecimal("65"), 10,
            RoundingMode.HALF_UP);
    private static final Locale CO = Locale.forLanguageTag("es-CO");

    private AlertRules() {
    }

    public enum Type {
        SALES_DROP,
        STOCKOUT_SOON,
        STOCKOUT,
        CUSTOMER_AT_RISK,
        DEAD_STOCK
    }

    public enum Severity {
        INFO,
        WARNING,
        CRITICAL
    }

    /** Umbrales efectivos: razón de caída de ventas, días de cobertura y días sin ventas para stock muerto. */
    public record Thresholds(BigDecimal salesDropRatio, double coverageDays, int deadStockDays) {
        public static Thresholds defaults() {
            return new Thresholds(new BigDecimal("0.65"), 7, 90);
        }
    }

    public record DaySales(LocalDate day, BigDecimal revenue) {
    }

    /**
     * Producto publicado del almacén. {@code coverageDays} es {@code null} sin ventas; {@code daysSinceLastSale}
     * es {@code null} sin ventas en el historial consultado.
     */
    public record ProductState(UUID productId, String name, String abcClass, Double coverageDays, long stock,
                               long unitsLast30, Integer daysSinceLastSale, long ageDays) {
    }

    /** Segmento actual y el de hace {@value #CUSTOMER_COMPARISON_DAYS} días ({@code null} si no existía). */
    public record CustomerState(UUID personId, String name, CustomerSegment current, CustomerSegment previous) {
    }

    public record Snapshot(UUID storeId, LocalDate today, List<DaySales> dailySales, List<ProductState> products,
                           List<CustomerState> customers) {
    }

    public record Candidate(Type type, Severity severity, String title, String message, Map<String, Object> payload,
                            String dedupeKey) {
    }

    public static List<Candidate> evaluate(Snapshot snapshot, Thresholds thresholds) {
        List<Candidate> candidates = new ArrayList<>();
        salesDrop(snapshot, thresholds).ifPresent(candidates::add);
        candidates.addAll(stockoutSoon(snapshot, thresholds));
        candidates.addAll(stockout(snapshot));
        candidates.addAll(customersAtRisk(snapshot));
        deadStock(snapshot, thresholds).ifPresent(candidates::add);
        return candidates;
    }

    /** Semana ISO {@code 2026-W45}. */
    public static String isoWeek(LocalDate day) {
        return "%d-W%02d".formatted(day.get(IsoFields.WEEK_BASED_YEAR), day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    static String dedupeKey(Type type, Object object, LocalDate today) {
        return type.name() + ":" + object + ":" + isoWeek(today);
    }

    // ---- SALES_DROP -------------------------------------------------------------------------------------------

    static Optional<Candidate> salesDrop(Snapshot snapshot, Thresholds thresholds) {
        LocalDate today = snapshot.today();
        LocalDate recentStart = today.minusDays(SALES_WINDOW_DAYS);
        LocalDate recentEnd = today.minusDays(1);
        LocalDate baselineStart = today.minusDays(SALES_WINDOW_DAYS * (long) (BASELINE_WEEKS + 1));
        LocalDate baselineEnd = recentStart.minusDays(1);

        LocalDate firstSale = snapshot.dailySales().stream()
                .filter(day -> day.revenue() != null && day.revenue().signum() > 0)
                .map(DaySales::day)
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (firstSale == null || firstSale.isAfter(baselineStart)) {
            return Optional.empty();
        }
        BigDecimal recent = sum(snapshot.dailySales(), recentStart, recentEnd);
        BigDecimal average = sum(snapshot.dailySales(), baselineStart, baselineEnd)
                .divide(BigDecimal.valueOf(BASELINE_WEEKS), 2, RoundingMode.HALF_UP);
        if (average.signum() <= 0 || recent.compareTo(average.multiply(thresholds.salesDropRatio())) >= 0) {
            return Optional.empty();
        }
        BigDecimal criticalRatio = thresholds.salesDropRatio().multiply(CRITICAL_SHARE);
        Severity severity = recent.compareTo(average.multiply(criticalRatio)) < 0
                ? Severity.CRITICAL : Severity.WARNING;
        BigDecimal ratio = recent.divide(average, 2, RoundingMode.HALF_UP);
        long percent = ratio.movePointRight(2).longValue();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("lastSevenDaysRevenue", recent.setScale(2, RoundingMode.HALF_UP));
        payload.put("weeklyAverageRevenue", average);
        payload.put("ratio", ratio);
        String message = "Las ventas de los últimos 7 días (%s) son el %d %% de tu promedio semanal de las 8 semanas "
                .formatted(money(recent), percent)
                + "anteriores (%s). Revisa precios, inventario y promociones.".formatted(money(average));
        return Optional.of(new Candidate(Type.SALES_DROP, severity, "Tus ventas bajaron esta semana", message,
                payload, dedupeKey(Type.SALES_DROP, snapshot.storeId(), today)));
    }

    private static BigDecimal sum(List<DaySales> days, LocalDate from, LocalDate to) {
        BigDecimal total = BigDecimal.ZERO;
        for (DaySales day : days) {
            if (day.revenue() != null && !day.day().isBefore(from) && !day.day().isAfter(to)) {
                total = total.add(day.revenue());
            }
        }
        return total;
    }

    // ---- Productos --------------------------------------------------------------------------------------------

    static List<Candidate> stockoutSoon(Snapshot snapshot, Thresholds thresholds) {
        List<Candidate> result = new ArrayList<>();
        for (ProductState product : snapshot.products()) {
            boolean important = ProductClassifier.A.equals(product.abcClass())
                    || ProductClassifier.B.equals(product.abcClass());
            if (!important || product.stock() <= 0 || product.coverageDays() == null
                    || product.coverageDays() >= thresholds.coverageDays()) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("productId", product.productId());
            payload.put("name", product.name());
            payload.put("abcClass", product.abcClass());
            payload.put("stock", product.stock());
            payload.put("coverageDays", Analytics.decimal(product.coverageDays(), 1));
            String message = "Con tu ritmo de ventas, «%s» (clase %s) se agota en unos %s días; quedan %d unidades. "
                    .formatted(product.name(), product.abcClass(), days(product.coverageDays()), product.stock())
                    + "Considera reponerlo.";
            result.add(new Candidate(Type.STOCKOUT_SOON, Severity.WARNING,
                    limit("Se agotará pronto: " + product.name(), 160), message, payload,
                    dedupeKey(Type.STOCKOUT_SOON, product.productId(), snapshot.today())));
        }
        return result;
    }

    static List<Candidate> stockout(Snapshot snapshot) {
        List<Candidate> result = new ArrayList<>();
        for (ProductState product : snapshot.products()) {
            if (product.stock() > 0 || product.unitsLast30() <= 0) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("productId", product.productId());
            payload.put("name", product.name());
            payload.put("unitsLast30Days", product.unitsLast30());
            String message = "«%s» está publicado sin existencias y vendiste %d unidades en los últimos 30 días. "
                    .formatted(product.name(), product.unitsLast30())
                    + "Repón el inventario para no perder ventas.";
            result.add(new Candidate(Type.STOCKOUT, Severity.CRITICAL,
                    limit("Producto agotado: " + product.name(), 160), message, payload,
                    dedupeKey(Type.STOCKOUT, product.productId(), snapshot.today())));
        }
        return result;
    }

    static Optional<Candidate> deadStock(Snapshot snapshot, Thresholds thresholds) {
        int days = thresholds.deadStockDays();
        List<ProductState> dead = snapshot.products().stream()
                .filter(product -> product.stock() > 0 && product.ageDays() >= days
                        && (product.daysSinceLastSale() == null || product.daysSinceLastSale() >= days))
                .sorted(Comparator.comparingLong(ProductState::stock).reversed()
                        .thenComparing(ProductState::name, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (dead.isEmpty()) {
            return Optional.empty();
        }
        List<Map<String, Object>> products = dead.stream().limit(DEAD_STOCK_PAYLOAD_LIMIT).map(product -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productId", product.productId());
            item.put("name", product.name());
            item.put("stock", product.stock());
            return item;
        }).toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("count", dead.size());
        payload.put("days", days);
        payload.put("products", products);
        String message = "%d %s con existencias no se %s en los últimos %d días. Revisa precios o promociones "
                .formatted(dead.size(), dead.size() == 1 ? "producto" : "productos",
                        dead.size() == 1 ? "ha vendido" : "han vendido", days)
                + "para rotarlos.";
        return Optional.of(new Candidate(Type.DEAD_STOCK, Severity.INFO, "Inventario sin movimiento", message,
                payload, dedupeKey(Type.DEAD_STOCK, snapshot.storeId(), snapshot.today())));
    }

    // ---- Clientes ---------------------------------------------------------------------------------------------

    static List<Candidate> customersAtRisk(Snapshot snapshot) {
        List<Candidate> result = new ArrayList<>();
        for (CustomerState customer : snapshot.customers()) {
            if (customer.previous() != CustomerSegment.FREQUENT || customer.current() != CustomerSegment.AT_RISK) {
                continue;
            }
            String name = customer.name() == null || customer.name().isBlank() ? "Un cliente frecuente"
                    : customer.name();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("personId", customer.personId());
            payload.put("previousSegment", CustomerSegment.FREQUENT.name());
            payload.put("segment", CustomerSegment.AT_RISK.name());
            String message = "%s era un cliente frecuente y ahora está en riesgo: lleva más de 60 días sin comprar. "
                    .formatted(limit(name, 200))
                    + "Una promoción dirigida puede ayudarte a recuperarlo.";
            result.add(new Candidate(Type.CUSTOMER_AT_RISK, Severity.INFO, "Cliente frecuente en riesgo", message,
                    payload, dedupeKey(Type.CUSTOMER_AT_RISK, customer.personId(), snapshot.today())));
        }
        return result;
    }

    // ---- Formato ----------------------------------------------------------------------------------------------

    private static String money(BigDecimal value) {
        NumberFormat format = NumberFormat.getIntegerInstance(CO);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return "$" + format.format(value);
    }

    private static String days(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(CO);
        format.setMaximumFractionDigits(1);
        format.setMinimumFractionDigits(0);
        return format.format(value);
    }

    private static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
