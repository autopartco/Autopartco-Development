package com.autopartco.backend.core.advertising;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Utilidades comunes del módulo: zona horaria y normalización de texto para la segmentación. */
public final class AdText {
    /** Zona de las fechas de campaña, del job de estados y del día de los eventos. */
    public static final ZoneId ZONE = ZoneId.of("America/Bogota");

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");

    private AdText() {
    }

    /** Hoy en Bogotá según {@code clock}. */
    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }

    /** Minúsculas, sin tildes, solo letras y números separados por un espacio. {@code null} → cadena vacía. */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String folded = MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("");
        return NON_WORD.matcher(folded.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    /** Palabras normalizadas de {@code value}. */
    public static List<String> words(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? List.of() : Arrays.asList(normalized.split(" "));
    }
}
