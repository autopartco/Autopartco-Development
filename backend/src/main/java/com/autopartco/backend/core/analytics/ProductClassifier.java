package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.autopartco.backend.core.analytics.ProductReport.ProductAction;

/** Reglas puras de clasificación de productos (ABC, XYZ, stock mínimo sugerido y acción). */
public final class ProductClassifier {
    public static final String A = "A";
    public static final String B = "B";
    public static final String C = "C";

    static final double A_SHARE = 0.80;
    static final double B_SHARE = 0.95;
    static final double X_MAX_CV = 0.5;
    static final double Y_MAX_CV = 1.0;
    static final int MIN_WEEKS_WITH_SALES = 4;
    static final int LEAD_TIME_DAYS = 7;
    static final double PROTECT_COVERAGE_DAYS = 14;
    static final double REVIEW_PRICE_COVERAGE_DAYS = 120;
    static final int LIQUIDATE_AFTER_DAYS = 180;

    private ProductClassifier() {
    }

    /**
     * Clase ABC por ingresos: se ordenan de mayor a menor y un producto es A si el acumulado de los anteriores es
     * menor que 80 %, B si es menor que 95 % y C en otro caso (así el primero siempre es A). Sin ingresos: C.
     */
    public static Map<UUID, String> abc(Map<UUID, BigDecimal> revenueByProduct) {
        Map<UUID, String> classes = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        List<Map.Entry<UUID, BigDecimal>> selling = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : revenueByProduct.entrySet()) {
            if (entry.getValue() != null && entry.getValue().signum() > 0) {
                selling.add(entry);
                total = total.add(entry.getValue());
            } else {
                classes.put(entry.getKey(), C);
            }
        }
        selling.sort(Map.Entry.<UUID, BigDecimal>comparingByValue(Comparator.reverseOrder())
                .thenComparing(entry -> entry.getKey().toString()));
        BigDecimal before = BigDecimal.ZERO;
        for (Map.Entry<UUID, BigDecimal> entry : selling) {
            double share = before.divide(total, 10, RoundingMode.HALF_UP).doubleValue();
            classes.put(entry.getKey(), share < A_SHARE ? A : share < B_SHARE ? B : C);
            before = before.add(entry.getValue());
        }
        return classes;
    }

    /**
     * Clase XYZ por el coeficiente de variación de las unidades semanales: menor que 0,5 X, menor que 1,0 Y, si no
     * Z. {@code null} si hay menos de {@value #MIN_WEEKS_WITH_SALES} semanas con ventas.
     */
    public static String xyz(long[] weeklyUnits) {
        long weeksWithSales = java.util.Arrays.stream(weeklyUnits).filter(units -> units > 0).count();
        if (weeksWithSales < MIN_WEEKS_WITH_SALES) {
            return null;
        }
        double mean = mean(weeklyUnits);
        double cv = standardDeviation(weeklyUnits, mean) / mean;
        return cv < X_MAX_CV ? "X" : cv < Y_MAX_CV ? "Y" : "Z";
    }

    /** {@code ceil(media·7 + z·desv·√7)} con z según la clase ABC (A 1,65; B 1,28; C 1,0). Sin ventas: null. */
    public static Integer suggestedMinStock(double avgDailyUnits, double dailyStdDev, String abcClass) {
        if (avgDailyUnits <= 0) {
            return null;
        }
        double z = switch (abcClass) {
            case A -> 1.65;
            case B -> 1.28;
            default -> 1.0;
        };
        double value = avgDailyUnits * LEAD_TIME_DAYS + z * dailyStdDev * Math.sqrt(LEAD_TIME_DAYS);
        return (int) Math.ceil(value - 1e-9);
    }

    /** Días de cobertura {@code stock / media diaria}; {@code null} si la media es 0. */
    public static Double coverageDays(long stock, double avgDailyUnits) {
        return avgDailyUnits <= 0 ? null : stock / avgDailyUnits;
    }

    /**
     * Acción recomendada: sin ventas y con stock, {@code LIQUIDATE} si el producto tiene más de
     * {@value #LIQUIDATE_AFTER_DAYS} días y si no {@code NO_MOVEMENT}; A con cobertura menor de 14 días,
     * {@code PROTECT_STOCK}; C con cobertura mayor de 120 días, {@code REVIEW_PRICE}; el resto {@code MAINTAIN}.
     */
    public static ProductAction action(String abcClass, long units, long stock, Double coverageDays,
                                       long productAgeDays) {
        if (units <= 0 && stock > 0) {
            return productAgeDays > LIQUIDATE_AFTER_DAYS ? ProductAction.LIQUIDATE : ProductAction.NO_MOVEMENT;
        }
        if (A.equals(abcClass) && coverageDays != null && coverageDays < PROTECT_COVERAGE_DAYS) {
            return ProductAction.PROTECT_STOCK;
        }
        if (C.equals(abcClass) && coverageDays != null && coverageDays > REVIEW_PRICE_COVERAGE_DAYS) {
            return ProductAction.REVIEW_PRICE;
        }
        return ProductAction.MAINTAIN;
    }

    static double mean(long[] values) {
        if (values.length == 0) {
            return 0;
        }
        double sum = 0;
        for (long value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    /** Desviación estándar poblacional. */
    static double standardDeviation(long[] values, double mean) {
        if (values.length == 0) {
            return 0;
        }
        double squares = 0;
        for (long value : values) {
            squares += (value - mean) * (value - mean);
        }
        return Math.sqrt(squares / values.length);
    }
}
