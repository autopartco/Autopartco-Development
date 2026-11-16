package com.autopartco.backend.core.ml;

/** Cuantiles de la distribución de Poisson, para el intervalo del pronóstico de demanda. */
final class Poisson {
    private static final int MAX_K = 100_000;

    private Poisson() {
    }

    /** Menor k con P(X ≤ k) ≥ q para X ~ Poisson(lambda). */
    static int quantile(double lambda, double q) {
        if (lambda <= 0) {
            return 0;
        }
        double pmf = Math.exp(-lambda);
        double cdf = pmf;
        int k = 0;
        if (pmf == 0) {
            // lambda muy grande: aproximación normal.
            return (int) Math.max(0, Math.round(lambda + inverseNormal(q) * Math.sqrt(lambda)));
        }
        while (cdf < q && k < MAX_K) {
            k++;
            pmf *= lambda / k;
            cdf += pmf;
        }
        return k;
    }

    private static double inverseNormal(double q) {
        return q < 0.5 ? -1.2815515655446004 : 1.2815515655446004;
    }
}
