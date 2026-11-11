package com.autopartco.backend.core.search;

import java.util.List;
import java.util.Locale;

/**
 * Resultado de {@link QueryParser#parse}: texto restante (palabras normalizadas), su {@code tsquery} con sinónimos y
 * prefijo en la última palabra, y lo reconocido (año, marca, modelo, ciudad, referencia). {@code tsQuery} solo
 * contiene {@code [a-z0-9]}, espacios, paréntesis y los operadores {@code & | :*}: siempre viaja como parámetro.
 * <p>
 * Lo reconocido es una pista: no filtra (eso solo lo hacen los parámetros explícitos), ordena. {@code anyTerms} son
 * los grupos del {@code tsquery} sin palabras vacías, para el respaldo «cualquier palabra»; {@code vehicleTsQuery}
 * ({@code marca | modelo}) y {@code modelTsQuery} buscan el vehículo reconocido en el texto del producto. Mismo
 * alfabeto que {@code tsQuery}.
 */
public record ParsedQuery(
        String normalized,
        List<String> words,
        String tsQuery,
        String reference,
        SearchDictionary.Make make,
        SearchDictionary.Model model,
        Integer year,
        SearchDictionary.City city,
        List<String> anyTerms,
        String vehicleTsQuery,
        String modelTsQuery
) {
    public static final ParsedQuery EMPTY = new ParsedQuery(null, List.of(), null, null, null, null, null, null,
            List.of(), null, null);

    public ParsedQuery {
        words = words == null ? List.of() : List.copyOf(words);
        anyTerms = anyTerms == null ? List.of() : List.copyOf(anyTerms);
    }

    /** {@code grupo | grupo | …} de las palabras de contenido, o {@code null} si son menos de dos. */
    public String anyTsQuery() {
        return anyTerms.size() < 2 ? null : String.join(" | ", anyTerms);
    }

    /** Texto restante (palabras separadas por un espacio) o {@code null}. */
    public String text() {
        return words.isEmpty() ? null : String.join(" ", words);
    }

    /** Hay algo que buscar como texto (palabras o referencia). */
    public boolean hasText() {
        return tsQuery != null || reference != null;
    }

    /** Texto para el respaldo por trigramas: el restante o, si no hay, la referencia en minúsculas. */
    public String fuzzyText() {
        if (text() != null) {
            return text();
        }
        return reference == null ? null : reference.toLowerCase(Locale.ROOT);
    }

    /** Lo reconocido, o {@code null} si no se reconoció nada. */
    public SearchInterpretation interpretation() {
        SearchInterpretation interpretation = new SearchInterpretation(
                text(),
                make == null ? null : make.id(),
                make == null ? null : make.name(),
                model == null ? null : model.id(),
                model == null ? null : model.name(),
                year,
                city == null ? null : city.code(),
                city == null ? null : city.name(),
                reference);
        return interpretation.recognizedSomething() ? interpretation : null;
    }
}
