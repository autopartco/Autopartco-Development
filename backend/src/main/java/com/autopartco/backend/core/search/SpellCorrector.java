package com.autopartco.backend.core.search;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Corrector ortográfico en memoria al estilo SymSpell (borrados precalculados) sobre el léxico del catálogo.
 * <p>
 * Cada palabra objetivo aporta los borrados de hasta {@value #MAX_DISTANCE} letras de su prefijo de {@value #PREFIX}
 * letras; se guardan solo sus hash de 32 bits en dos {@code int[]} paralelos ordenados (hash, palabra), sin
 * {@code String} por borrado. Al corregir se generan los borrados del prefijo del token, se buscan por búsqueda
 * binaria y cada candidato se verifica con la distancia de Damerau restringida (la de {@link QueryParser#distance},
 * acotada) sobre la palabra completa: una colisión de hash solo cuesta una verificación de más.
 * <p>
 * Reglas de {@link #correct}: solo tokens de letras de {@value #MIN_LENGTH} o más que no estén en el léxico (ni su
 * singular o plural); distancia ≤1 hasta 5 letras y ≤2 desde 6; gana la menor distancia y, a igual distancia, la mayor
 * frecuencia; si persiste el empate, no corrige. El candidato debe empezar como el token: misma letra, las dos
 * primeras transpuestas, una confusión fonética del español ({@code b/v}, {@code c/s/z}, {@code c/k/q}, {@code g/j},
 * {@code i/y}) o una {@code h} muda de más o de menos; así una palabra válida ajena al catálogo («litros») no se
 * convierte en otra que solo comparte el final («filtros»). Las palabras «conocidas» (frecuencia 0: palabras vacías,
 * municipios, prefijos de referencia) nunca se corrigen ni son destino de una corrección. Inmutable y seguro entre
 * hilos.
 */
final class SpellCorrector {
    static final int MIN_LENGTH = 4;
    static final int MAX_DISTANCE = 2;
    static final int PREFIX = 7;
    static final int MAX_WORD_LENGTH = 30;

    static final SpellCorrector EMPTY = new SpellCorrector(Map.of());

    private final String[] words;
    private final int[] frequencies;
    /** Tabla abierta: índice de palabra + 1 (0 = vacío), por {@code String.hashCode}. */
    private final int[] table;
    private final int[] deleteHashes;
    private final int[] deleteWords;

    /** {@code lexicon}: palabra normalizada → frecuencia (documentos); 0 = conocida pero nunca destino. */
    SpellCorrector(Map<String, Integer> lexicon) {
        int size = lexicon.size();
        words = new String[size];
        frequencies = new int[size];
        int i = 0;
        for (Map.Entry<String, Integer> entry : lexicon.entrySet()) {
            words[i] = entry.getKey();
            frequencies[i] = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
            i++;
        }
        int capacity = Integer.highestOneBit(Math.max(4, size * 2 - 1)) << 1;
        table = new int[capacity];
        for (int w = 0; w < size; w++) {
            int slot = words[w].hashCode() & (capacity - 1);
            while (table[slot] != 0) {
                slot = (slot + 1) & (capacity - 1);
            }
            table[slot] = w + 1;
        }
        // (hash del borrado << 32 | palabra), ordenado y sin duplicados.
        long[] pairs = new long[Math.max(16, size * 16)];
        int count = 0;
        Set<String> deletes = new HashSet<>();
        for (int w = 0; w < size; w++) {
            if (frequencies[w] == 0) {
                continue;
            }
            deletes.clear();
            String word = words[w];
            deletes(word.substring(0, Math.min(PREFIX, word.length())), MAX_DISTANCE, deletes);
            for (String delete : deletes) {
                if (count == pairs.length) {
                    pairs = Arrays.copyOf(pairs, pairs.length * 2);
                }
                pairs[count++] = ((long) delete.hashCode() << 32) | w;
            }
        }
        Arrays.sort(pairs, 0, count);
        deleteHashes = new int[count];
        deleteWords = new int[count];
        for (int p = 0; p < count; p++) {
            deleteHashes[p] = (int) (pairs[p] >> 32);
            deleteWords[p] = (int) pairs[p];
        }
    }

    int size() {
        return words.length;
    }

    /** La palabra está en el léxico (como destino o como conocida). */
    boolean contains(String word) {
        return index(word) >= 0;
    }

    /** Frecuencia de la palabra, 0 si es solo conocida, -1 si no está. */
    int frequency(String word) {
        int w = index(word);
        return w < 0 ? -1 : frequencies[w];
    }

    /** La palabra, su singular o su plural están en el léxico: no es un error ortográfico. */
    boolean known(String word) {
        if (contains(word)) {
            return true;
        }
        if (word.endsWith("es") && contains(word.substring(0, word.length() - 2))) {
            return true;
        }
        if (word.endsWith("s") && contains(word.substring(0, word.length() - 1))) {
            return true;
        }
        return contains(word + "s") || contains(word + "es");
    }

    /** La corrección del token, o vacío si no aplica, ya es válido o es ambigua. */
    Optional<String> correct(String token) {
        if (token == null || token.length() < MIN_LENGTH || token.length() > MAX_WORD_LENGTH || !letters(token)
                || QueryParser.STOPWORDS.contains(token) || deleteHashes.length == 0 || known(token)) {
            return Optional.empty();
        }
        int max = token.length() <= 5 ? 1 : MAX_DISTANCE;
        Set<String> deletes = new HashSet<>();
        deletes(token.substring(0, Math.min(PREFIX, token.length())), max, deletes);
        Set<Integer> seen = new HashSet<>();
        int bestDistance = Integer.MAX_VALUE;
        int bestFrequency = -1;
        String best = null;
        boolean tie = false;
        for (String delete : deletes) {
            int hash = delete.hashCode();
            int from = lowerBound(hash);
            for (int p = from; p < deleteHashes.length && deleteHashes[p] == hash; p++) {
                int w = deleteWords[p];
                if (!seen.add(w)) {
                    continue;
                }
                String candidate = words[w];
                if (Math.abs(candidate.length() - token.length()) > max || !sameStart(token, candidate)) {
                    continue;
                }
                int distance = boundedDistance(token, candidate, max);
                if (distance > max || distance > bestDistance) {
                    continue;
                }
                int frequency = frequencies[w];
                if (distance < bestDistance || frequency > bestFrequency) {
                    bestDistance = distance;
                    bestFrequency = frequency;
                    best = candidate;
                    tie = false;
                } else if (frequency == bestFrequency) {
                    tie = true;
                }
            }
        }
        return best == null || tie ? Optional.empty() : Optional.of(best);
    }

    /** Memoria aproximada del léxico en bytes (arreglos y cadenas, JVM de 64 bits con referencias comprimidas). */
    long approximateBytes() {
        long bytes = 16L + 4L * words.length + 16L + 4L * frequencies.length + 16L + 4L * table.length
                + 2 * (16L + 4L * deleteHashes.length);
        for (String word : words) {
            bytes += 24 + 16 + word.length();
        }
        return bytes;
    }

    private int index(String word) {
        if (word == null || words.length == 0) {
            return -1;
        }
        int mask = table.length - 1;
        for (int slot = word.hashCode() & mask; table[slot] != 0; slot = (slot + 1) & mask) {
            int w = table[slot] - 1;
            if (words[w].equals(word)) {
                return w;
            }
        }
        return -1;
    }

    private int lowerBound(int hash) {
        int low = 0;
        int high = deleteHashes.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (deleteHashes[mid] < hash) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    /**
     * Damerau restringida entre {@code a} y {@code b}, o {@code max + 1} en cuanto se sabe que la supera: tres filas en
     * vez de la matriz y corte cuando toda una fila pasa de {@code max}.
     */
    static int boundedDistance(String a, String b, int max) {
        int n = a.length();
        int m = b.length();
        if (Math.abs(n - m) > max) {
            return max + 1;
        }
        int[] before = new int[m + 1];
        int[] previous = new int[m + 1];
        int[] current = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            current[0] = i;
            int rowMin = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                char cb = b.charAt(j - 1);
                int value = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1),
                        previous[j - 1] + (ca == cb ? 0 : 1));
                if (i > 1 && j > 1 && ca == b.charAt(j - 2) && a.charAt(i - 2) == cb) {
                    value = Math.min(value, before[j - 2] + 1);
                }
                current[j] = value;
                rowMin = Math.min(rowMin, value);
            }
            if (rowMin > max) {
                return max + 1;
            }
            int[] recycled = before;
            before = previous;
            previous = current;
            current = recycled;
        }
        return Math.min(previous[m], max + 1);
    }

    private static final String[] SOUND_ALIKE = {"bv", "csz", "ckq", "gj", "iy"};

    /** Mismo comienzo, salvo transposición inicial, confusión fonética o {@code h} muda (ver la clase). */
    static boolean sameStart(String token, String candidate) {
        char a = token.charAt(0);
        char b = candidate.charAt(0);
        if (a == b) {
            return true;
        }
        if (token.length() > 1 && candidate.length() > 1 && a == candidate.charAt(1) && token.charAt(1) == b) {
            return true;
        }
        if ((a == 'h' && token.length() > 1 && token.charAt(1) == b)
                || (b == 'h' && candidate.length() > 1 && candidate.charAt(1) == a)) {
            return true;
        }
        for (String group : SOUND_ALIKE) {
            if (group.indexOf(a) >= 0 && group.indexOf(b) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** {@code word} y todos sus borrados de hasta {@code depth} letras. */
    private static void deletes(String word, int depth, Set<String> out) {
        if (!out.add(word) || depth == 0 || word.length() <= 1) {
            return;
        }
        for (int i = 0; i < word.length(); i++) {
            deletes(word.substring(0, i) + word.substring(i + 1), depth - 1, out);
        }
    }

    private static boolean letters(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c < 'a' || c > 'z') {
                return false;
            }
        }
        return true;
    }
}
