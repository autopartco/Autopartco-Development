package com.autopartco.backend.core.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SpellCorrectorTest {

    private static SpellCorrector corrector(Object... pairs) {
        Map<String, Integer> lexicon = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            lexicon.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return new SpellCorrector(lexicon);
    }

    @Test
    void correctsOneEditInShortWordsAndTwoInLongOnes() {
        SpellCorrector speller = corrector("freno", 10, "radiador", 5, "amortiguador", 8, "pastillas", 9);

        assertThat(speller.correct("frneo")).contains("freno");
        assertThat(speller.correct("fren")).contains("freno");
        assertThat(speller.correct("radidor")).contains("radiador");
        assertThat(speller.correct("amortigudor")).contains("amortiguador");
        assertThat(speller.correct("pastiyas")).contains("pastillas");
        // Dos errores en una palabra de 5 letras: no se corrige.
        assertThat(speller.correct("fxenx")).isEmpty();
        // Tres errores en una palabra larga: no se corrige.
        assertThat(speller.correct("rxdixdxr")).isEmpty();
    }

    @Test
    void lowerDistanceWinsThenHigherFrequencyAndARealTieDoesNothing() {
        assertThat(corrector("bombillo", 2, "bombilla", 100).correct("bomvillo")).contains("bombillo");
        assertThat(corrector("casa", 5, "cosa", 9).correct("cxsa")).contains("cosa");
        assertThat(corrector("casa", 5, "cosa", 5).correct("cxsa")).isEmpty();
    }

    @Test
    void validRareWordsShortTokensNumbersAndReferencesAreNeverTouched() {
        SpellCorrector speller = corrector("tensor", 1, "sensor", 100, "freno", 10, "bujia", 4);

        assertThat(speller.correct("tensor")).isEmpty();
        assertThat(speller.correct("fre")).isEmpty();
        assertThat(speller.correct("5w30")).isEmpty();
        assertThat(speller.correct("h4")).isEmpty();
        assertThat(speller.correct("bkr6e")).isEmpty();
        assertThat(speller.correct("para")).isEmpty();
        assertThat(speller.correct("bujias")).as("plural de una palabra del léxico").isEmpty();
    }

    @Test
    void theCandidateMustStartLikeTheTokenSaveSpanishSoundAlikes() {
        SpellCorrector speller = corrector("filtros", 10, "bujia", 5, "horquilla", 3, "zapata", 4, "empaque", 6);

        assertThat(speller.correct("litros")).as("palabra válida ajena al catálogo").isEmpty();
        assertThat(speller.correct("vujia")).contains("bujia");
        assertThat(speller.correct("orquilla")).contains("horquilla");
        assertThat(speller.correct("sapata")).contains("zapata");
        assertThat(speller.correct("mepaque")).as("dos primeras transpuestas").contains("empaque");
    }

    @Test
    void knownOnlyWordsAreNeitherCorrectedNorTargets() {
        SpellCorrector speller = corrector("medellin", 0, "bota", 3);

        assertThat(speller.contains("medellin")).isTrue();
        assertThat(speller.frequency("medellin")).isZero();
        assertThat(speller.correct("medellin")).isEmpty();
        assertThat(speller.correct("medelin")).isEmpty();
        assertThat(SpellCorrector.EMPTY.correct("radidor")).isEmpty();
    }

    @Test
    void boundedDistanceAgreesWithTheParserDistance() {
        Random random = new Random(7);
        for (int i = 0; i < 20_000; i++) {
            String a = randomWord(random);
            String b = random.nextBoolean() ? randomWord(random) : mutate(a, random);
            int exact = QueryParser.distance(a, b);
            for (int max = 0; max <= 3; max++) {
                assertThat(SpellCorrector.boundedDistance(a, b, max)).as("%s / %s ≤ %d", a, b, max)
                        .isEqualTo(Math.min(exact, max + 1));
            }
        }
    }

    private static String randomWord(Random random) {
        StringBuilder word = new StringBuilder();
        int length = 1 + random.nextInt(8);
        for (int i = 0; i < length; i++) {
            word.append((char) ('a' + random.nextInt(4)));
        }
        return word.toString();
    }

    private static String mutate(String word, Random random) {
        StringBuilder out = new StringBuilder(word);
        for (int edits = random.nextInt(3); edits >= 0; edits--) {
            int at = random.nextInt(out.length() + 1);
            switch (random.nextInt(4)) {
                case 0 -> out.insert(at, (char) ('a' + random.nextInt(4)));
                case 1 -> { if (at < out.length() && out.length() > 1) out.deleteCharAt(at); }
                case 2 -> { if (at < out.length()) out.setCharAt(at, (char) ('a' + random.nextInt(4))); }
                default -> {
                    if (at + 1 < out.length()) {
                        char c = out.charAt(at);
                        out.setCharAt(at, out.charAt(at + 1));
                        out.setCharAt(at + 1, c);
                    }
                }
            }
        }
        return out.toString();
    }

    /** Léxico sintético de 50 000 palabras de sílabas del español: memoria, carga y µs por corrección. */
    @Test
    void fiftyThousandWordsFitTheBudget() {
        String[] syllables = {"ba", "be", "bo", "ca", "ce", "co", "cu", "da", "de", "di", "do", "fa", "fe", "fi", "ga",
                "go", "la", "le", "li", "lo", "ma", "me", "mi", "mo", "na", "ne", "ni", "no", "pa", "pe", "pi", "po",
                "ra", "re", "ri", "ro", "sa", "se", "si", "so", "ta", "te", "ti", "to", "tor", "dor", "men", "cion",
                "tra", "tre", "bra", "pla", "gui", "que", "llo", "lla", "rra", "zo", "za", "es", "an", "en", "in", "al"};
        Random random = new Random(42);
        Set<String> unique = new LinkedHashSet<>();
        while (unique.size() < 50_000) {
            StringBuilder word = new StringBuilder();
            int parts = 2 + random.nextInt(4);
            for (int i = 0; i < parts; i++) {
                word.append(syllables[random.nextInt(syllables.length)]);
            }
            if (word.length() >= 4 && word.length() <= 16) {
                unique.add(word.toString());
            }
        }
        Map<String, Integer> lexicon = new HashMap<>();
        for (String word : unique) {
            lexicon.put(word, 1 + random.nextInt(500));
        }

        System.gc();
        long heapBefore = usedHeap();
        long buildStart = System.nanoTime();
        SpellCorrector speller = new SpellCorrector(lexicon);
        double buildMs = (System.nanoTime() - buildStart) / 1_000_000.0;
        lexicon = null;
        System.gc();
        long heapAfter = usedHeap();

        List<String> words = new ArrayList<>(unique);
        List<String> typos = new ArrayList<>();
        for (int i = 0; i < 5_000; i++) {
            String word = words.get(random.nextInt(words.size()));
            int at = random.nextInt(word.length());
            typos.add(word.substring(0, at) + word.substring(at + 1)); // una letra omitida
        }
        for (String typo : typos.subList(0, 500)) {
            speller.correct(typo); // calentamiento
        }
        int corrected = 0;
        long start = System.nanoTime();
        for (String typo : typos) {
            if (speller.correct(typo).isPresent()) {
                corrected++;
            }
        }
        double microsPerCorrection = (System.nanoTime() - start) / 1_000.0 / typos.size();
        double megabytes = speller.approximateBytes() / 1_048_576.0;
        System.out.printf(Locale.ROOT, "[spell] 50000 palabras: %.0f ms de carga, %.1f MB estimados, %.1f MB medidos "
                + "en el heap, %.1f µs por corrección, %d de %d corregidas%n", buildMs, megabytes,
                (heapAfter - heapBefore) / 1_048_576.0, microsPerCorrection, corrected, typos.size());

        assertThat(speller.size()).isEqualTo(50_000);
        assertThat(megabytes).isLessThan(20.0);
        // Una consulta corrige como mucho unas pocas palabras: tres correcciones por debajo de 1 ms.
        assertThat(microsPerCorrection * 3).isLessThan(1_000.0);
        assertThat(corrected).isGreaterThan(typos.size() / 2);
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
