package com.autopartco.backend.core.ml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Modelo exportado por {@code ml/autopartco_ml/export.py}: ensamble de árboles en arreglos paralelos por nodo
 * ({@code feature} = -1 en las hojas) y metadatos. Inmutable y seguro entre hilos.
 *
 * <p>Regla de decisión, idéntica a la del entrenamiento: {@code (double)(float) x <= umbral} va a la izquierda.
 * Salidas: {@code POISSON_SUM_EXP} = exp(Σ hojas); {@code PROBABILITY_MEAN} = media de hojas con calibración isotónica
 * opcional (interpolación lineal); {@code ISOLATION_SCORE} = 2^(−media de longitudes de camino / c(n)).</p>
 */
public final class TreeEnsemble {
    public enum Output { POISSON_SUM_EXP, PROBABILITY_MEAN, ISOLATION_SCORE }

    private final JsonNode metadata;
    private final List<String> features;
    private final Output output;
    private final int[][] feature;
    private final double[][] threshold;
    private final int[][] left;
    private final int[][] right;
    private final double[][] value;
    private final double[] calibrationX;
    private final double[] calibrationY;
    private final double averagePathLength;

    private TreeEnsemble(JsonNode json) {
        this.metadata = json;
        this.features = new ArrayList<>();
        json.path("features").forEach(node -> features.add(node.asText()));
        this.output = Output.valueOf(json.path("output").asText());
        JsonNode trees = json.path("trees");
        int count = trees.size();
        feature = new int[count][];
        threshold = new double[count][];
        left = new int[count][];
        right = new int[count][];
        value = new double[count][];
        for (int t = 0; t < count; t++) {
            JsonNode tree = trees.get(t);
            feature[t] = ints(tree.path("feature"));
            threshold[t] = doubles(tree.path("threshold"));
            left[t] = ints(tree.path("left"));
            right[t] = ints(tree.path("right"));
            value[t] = doubles(tree.path("value"));
            validate(t);
        }
        JsonNode calibration = json.path("calibration");
        calibrationX = calibration.isObject() ? doubles(calibration.path("x")) : null;
        calibrationY = calibration.isObject() ? doubles(calibration.path("y")) : null;
        averagePathLength = json.path("isolation").path("averagePathLength").asDouble(1);
        if (count == 0 || features.isEmpty()) {
            throw new IllegalArgumentException("Modelo sin árboles o sin variables");
        }
    }

    public static TreeEnsemble of(JsonNode json) {
        return new TreeEnsemble(json);
    }

    public String key() {
        return metadata.path("key").asText();
    }

    public String version() {
        return metadata.path("version").asText();
    }

    public List<String> features() {
        return List.copyOf(features);
    }

    /** Metadatos de {@code model.json} (algoritmo, métricas, umbrales…). */
    public JsonNode metadata() {
        return metadata;
    }

    /** Vector de variables en el orden del modelo; falla si falta una columna o es nula. */
    /**
     * Mayor umbral con el que algún árbol divide por {@code name}: por encima de ese valor el modelo ya no distingue y
     * su respuesta queda plana. {@link Double#NEGATIVE_INFINITY} si ningún árbol usa la variable.
     */
    public double splitMax(String name) {
        int index = features.indexOf(name);
        double max = Double.NEGATIVE_INFINITY;
        for (int t = 0; index >= 0 && t < feature.length; t++) {
            for (int n = 0; n < feature[t].length; n++) {
                if (feature[t][n] == index) {
                    max = Math.max(max, threshold[t][n]);
                }
            }
        }
        return max;
    }

    public double[] vector(Map<String, Object> row) {
        double[] x = new double[features.size()];
        for (int i = 0; i < x.length; i++) {
            Object cell = row.get(features.get(i));
            if (!(cell instanceof Number number)) {
                throw new IllegalArgumentException("Variable ausente o nula: " + features.get(i));
            }
            x[i] = number.doubleValue();
        }
        return x;
    }

    public double predict(double[] raw) {
        if (raw.length != features.size()) {
            throw new IllegalArgumentException("Se esperaban " + features.size() + " variables");
        }
        double[] x = new double[raw.length];
        for (int i = 0; i < raw.length; i++) {
            x[i] = (float) raw[i];
        }
        double sum = 0;
        for (int t = 0; t < feature.length; t++) {
            sum += leaf(t, x);
        }
        return switch (output) {
            case POISSON_SUM_EXP -> Math.exp(sum);
            case PROBABILITY_MEAN -> calibrate(sum / feature.length);
            case ISOLATION_SCORE -> Math.pow(2.0, -(sum / feature.length) / averagePathLength);
        };
    }

    private double leaf(int t, double[] x) {
        int[] f = feature[t];
        double[] th = threshold[t];
        int node = 0;
        while (f[node] >= 0) {
            node = x[f[node]] <= th[node] ? left[t][node] : right[t][node];
        }
        return value[t][node];
    }

    /** Igual que {@code numpy.interp}: lineal entre puntos y constante fuera del rango. */
    private double calibrate(double p) {
        if (calibrationX == null || calibrationX.length == 0) {
            return p;
        }
        int last = calibrationX.length - 1;
        if (p <= calibrationX[0]) {
            return calibrationY[0];
        }
        if (p >= calibrationX[last]) {
            return calibrationY[last];
        }
        int hi = Arrays.binarySearch(calibrationX, p);
        if (hi >= 0) {
            while (hi < last && calibrationX[hi + 1] == p) {
                hi++;
            }
            return calibrationY[hi];
        }
        hi = -hi - 1;
        int lo = hi - 1;
        double span = calibrationX[hi] - calibrationX[lo];
        return calibrationY[lo] + (calibrationY[hi] - calibrationY[lo]) * (p - calibrationX[lo]) / span;
    }

    private void validate(int t) {
        int nodes = feature[t].length;
        if (nodes == 0 || threshold[t].length != nodes || left[t].length != nodes || right[t].length != nodes
                || value[t].length != nodes) {
            throw new IllegalArgumentException("Árbol " + t + " mal formado");
        }
        for (int n = 0; n < nodes; n++) {
            if (feature[t][n] >= features.size()
                    || (feature[t][n] >= 0 && (left[t][n] <= n || right[t][n] <= n
                    || left[t][n] >= nodes || right[t][n] >= nodes))) {
                throw new IllegalArgumentException("Árbol " + t + " con un nodo inválido");
            }
        }
    }

    private static int[] ints(JsonNode array) {
        int[] out = new int[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).asInt();
        }
        return out;
    }

    private static double[] doubles(JsonNode array) {
        double[] out = new double[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).asDouble();
        }
        return out;
    }
}
