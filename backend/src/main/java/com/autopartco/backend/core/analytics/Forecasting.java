package com.autopartco.backend.core.analytics;

/**
 * Métodos de pronóstico puros sobre series semanales.
 * <ul>
 *   <li>Holt lineal amortiguado (alpha {@value #ALPHA}, beta {@value #BETA}, phi {@value #PHI}). Nivel inicial = primer
 *   valor; tendencia inicial = pendiente media de los primeros (hasta 3) pasos.</li>
 *   <li>Con {@value #SEASON_LENGTH} semanas o más, estacionalidad aditiva anual simple: índice de cada semana del ciclo =
 *   media de esa semana − media global; Holt sobre la serie desestacionalizada y se suma el índice.</li>
 *   <li>Intervalo: ±{@value #Z}·RMSE de los errores a un paso·√h.</li>
 * </ul>
 */
public final class Forecasting {
    public static final String HOLT_DAMPED = "HOLT_DAMPED";
    public static final String HOLT_WINTERS_ADDITIVE = "HOLT_WINTERS_ADDITIVE";

    static final double ALPHA = 0.4;
    static final double BETA = 0.1;
    static final double PHI = 0.9;
    static final double Z = 1.96;
    static final int SEASON_LENGTH = 52;
    static final double SES_ALPHA = 0.3;

    private Forecasting() {
    }

    /** Resultado: método, pronóstico puntual para h = 1..horizon ({@code forecasts[h-1]}) y RMSE a un paso. */
    public record Model(String method, double[] forecasts, double rmse) {
        public double forecast(int h) {
            return forecasts[h - 1];
        }

        /** Semiancho del intervalo al 95 % para el paso {@code h}. */
        public double halfWidth(int h) {
            return Z * rmse * Math.sqrt(h);
        }
    }

    /** Ajusta Holt amortiguado, con estacionalidad anual si la serie tiene al menos 52 semanas. */
    public static Model fit(double[] series, int horizon) {
        if (series.length == 0) {
            throw new IllegalArgumentException("series must not be empty");
        }
        if (series.length < SEASON_LENGTH) {
            Holt holt = holt(series, horizon);
            return new Model(HOLT_DAMPED, holt.forecasts(), holt.rmse());
        }
        double mean = 0;
        for (double value : series) {
            mean += value;
        }
        mean /= series.length;
        double[] seasonal = new double[SEASON_LENGTH];
        int[] counts = new int[SEASON_LENGTH];
        for (int t = 0; t < series.length; t++) {
            seasonal[t % SEASON_LENGTH] += series[t];
            counts[t % SEASON_LENGTH]++;
        }
        for (int j = 0; j < SEASON_LENGTH; j++) {
            seasonal[j] = seasonal[j] / counts[j] - mean;
        }
        double[] adjusted = new double[series.length];
        for (int t = 0; t < series.length; t++) {
            adjusted[t] = series[t] - seasonal[t % SEASON_LENGTH];
        }
        Holt holt = holt(adjusted, horizon);
        double[] forecasts = new double[horizon];
        int last = series.length - 1;
        for (int h = 1; h <= horizon; h++) {
            forecasts[h - 1] = holt.forecasts()[h - 1] + seasonal[(last + h) % SEASON_LENGTH];
        }
        return new Model(HOLT_WINTERS_ADDITIVE, forecasts, holt.rmse());
    }

    record Holt(double[] forecasts, double rmse) {
    }

    static Holt holt(double[] y, int horizon) {
        double level = y[0];
        int initialSteps = Math.min(3, y.length - 1);
        double trend = initialSteps == 0 ? 0 : (y[initialSteps] - y[0]) / initialSteps;
        double squaredErrors = 0;
        int errors = 0;
        for (int t = 1; t < y.length; t++) {
            double predicted = level + PHI * trend;
            double error = y[t] - predicted;
            squaredErrors += error * error;
            errors++;
            double previousLevel = level;
            level = ALPHA * y[t] + (1 - ALPHA) * (previousLevel + PHI * trend);
            trend = BETA * (level - previousLevel) + (1 - BETA) * PHI * trend;
        }
        double[] forecasts = new double[horizon];
        double dampedSum = 0;
        double phiPower = 1;
        for (int h = 1; h <= horizon; h++) {
            phiPower *= PHI;
            dampedSum += phiPower;
            forecasts[h - 1] = level + dampedSum * trend;
        }
        return new Holt(forecasts, errors == 0 ? 0 : Math.sqrt(squaredErrors / errors));
    }

    /** Suavizado exponencial simple (alpha {@value #SES_ALPHA}): último nivel; nivel inicial = primer valor. */
    public static double simpleExponentialSmoothing(double[] series) {
        if (series.length == 0) {
            return 0;
        }
        double level = series[0];
        for (int t = 1; t < series.length; t++) {
            level = SES_ALPHA * series[t] + (1 - SES_ALPHA) * level;
        }
        return level;
    }
}
