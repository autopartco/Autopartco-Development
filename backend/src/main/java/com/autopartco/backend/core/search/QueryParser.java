package com.autopartco.backend.core.search;

import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.autopartco.backend.core.analytics.SearchText;
import com.autopartco.backend.core.search.SearchDictionary.City;
import com.autopartco.backend.core.search.SearchDictionary.Make;
import com.autopartco.backend.core.search.SearchDictionary.Model;
import com.autopartco.backend.core.search.SearchDictionary.Vocabulary;

/**
 * Interpreta el texto libre del comprador (§1 del plan v2.0.0, «Interpretación de la consulta»):
 * <ol>
 *   <li>Normaliza: minúsculas, sin tildes, espacios simples, máximo {@value #MAX_LENGTH} caracteres.</li>
 *   <li>Reconoce el año ({@code 19xx|20xx}, de 1950 al año siguiente), referencias ({@code [A-Z0-9-]{5,}} con algún
 *   dígito), marcas y modelos del catálogo (exactos o, en palabras de 5 letras o más, con un error de edición,
 *   transposiciones incluidas) y municipios DIVIPOLA por nombre.</li>
 *   <li>Expande el texto restante con sinónimos y arma un {@code tsquery} con prefijo en la última palabra.</li>
 * </ol>
 * No consulta la BD: usa {@link SearchDictionary}.
 */
@Component
public class QueryParser {
    public static final int MAX_LENGTH = 120;
    static final int MAX_WORDS = 12;
    static final int MAX_NGRAM = 4;
    static final int FUZZY_MIN_LENGTH = 5;
    static final String BOGOTA_CODE = "11001";
    /**
     * Palabras vacías frecuentes en las búsquedas: no cuentan en el respaldo «cualquier palabra» ni llevan prefijo.
     * La configuración {@code spanish_unaccent} también las descarta del {@code tsquery}.
     */
    static final Set<String> STOPWORDS = Set.of("a", "al", "con", "de", "del", "el", "en", "la", "las", "lo", "los",
            "para", "por", "sin", "su", "un", "una", "y", "o", "e", "u", "que", "mi");

    private static final Pattern YEAR = Pattern.compile("(19|20)\\d{2}");
    private static final Pattern REFERENCE = Pattern.compile("[A-Z0-9-]{5,}");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*\\d.*");
    private static final Pattern LETTERS = Pattern.compile("[a-z]+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern TOKEN_JUNK = Pattern.compile("[^a-z0-9-]+");

    private final SearchDictionary dictionary;
    private volatile Index index;

    public QueryParser(SearchDictionary dictionary) {
        this.dictionary = dictionary;
    }

    /** Minúsculas, sin tildes, solo {@code [a-z0-9]} separados por un espacio; {@code null} si queda vacío. */
    public static String normalizeName(String value) {
        String normalized = SearchText.normalize(value);
        if (normalized == null) {
            return null;
        }
        String clean = NON_ALNUM.matcher(normalized).replaceAll(" ").strip();
        return clean.isEmpty() ? null : clean;
    }

    /** {@code interpret=false}: no reconoce nada; todo el texto se busca como texto (con sinónimos). */
    public ParsedQuery parse(String raw, boolean interpret) {
        String normalized = SearchText.normalize(raw);
        if (normalized == null) {
            return ParsedQuery.EMPTY;
        }
        if (normalized.length() > MAX_LENGTH) {
            normalized = normalized.substring(0, MAX_LENGTH).strip();
        }
        Index lookup = index();
        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split(" ")) {
            String clean = TOKEN_JUNK.matcher(token).replaceAll("");
            clean = clean.replaceAll("^-+|-+$", "");
            if (!clean.isEmpty()) {
                tokens.add(clean);
            }
        }
        boolean[] used = new boolean[tokens.size()];
        Integer year = null;
        String reference = null;
        Make make = null;
        Model model = null;
        City city = null;
        if (interpret) {
            year = findYear(tokens, used);
            reference = findReference(tokens, used);
            Match<Model> combo = longest(tokens, used, 2, MAX_NGRAM, key -> single(lookup.modelsByFullName.get(key)));
            if (combo != null) {
                model = combo.value();
                make = lookup.makesById.get(model.makeId());
            }
            if (make == null) {
                Match<Make> exactMake = longest(tokens, used, 1, 3, lookup.makesByName::get);
                make = exactMake == null ? null : exactMake.value();
            }
            if (model == null) {
                UUID makeId = make == null ? null : make.id();
                Match<Model> exactModel = longest(tokens, used, 1, 3,
                        key -> pickModel(lookup.modelsByName.get(key), makeId));
                if (exactModel != null) {
                    model = exactModel.value();
                    make = lookup.makesById.get(model.makeId());
                }
            }
            Match<City> exactCity = longest(tokens, used, 1, 3,
                    key -> key.length() >= 4 ? lookup.citiesByName.get(key) : null);
            city = exactCity == null ? null : exactCity.value();
            if (make == null || model == null) {
                Make fuzzyMake = make;
                Model fuzzyModel = model;
                for (int i = 0; i < tokens.size(); i++) {
                    String token = tokens.get(i);
                    if (used[i] || token.length() < FUZZY_MIN_LENGTH || !LETTERS.matcher(token).matches()
                            || lookup.domainWords.contains(token)) {
                        continue;
                    }
                    if (fuzzyMake == null) {
                        Make candidate = closest(token, lookup.fuzzyMakes);
                        if (candidate != null) {
                            fuzzyMake = candidate;
                            used[i] = true;
                            continue;
                        }
                    }
                    if (fuzzyModel == null) {
                        UUID makeId = fuzzyMake == null ? null : fuzzyMake.id();
                        Model candidate = pickModel(closestAll(token, lookup.fuzzyModels), makeId);
                        if (candidate != null) {
                            fuzzyModel = candidate;
                            fuzzyMake = lookup.makesById.get(candidate.makeId());
                            used[i] = true;
                        }
                    }
                }
                make = fuzzyMake;
                model = fuzzyModel;
            }
        }
        List<String> words = new ArrayList<>();
        for (int i = 0; i < tokens.size() && words.size() < MAX_WORDS; i++) {
            if (used[i]) {
                continue;
            }
            for (String word : NON_ALNUM.matcher(tokens.get(i)).replaceAll(" ").strip().split(" ")) {
                if (!word.isEmpty() && words.size() < MAX_WORDS) {
                    words.add(word);
                }
            }
        }
        List<String> groups = groups(words, lookup.synonyms);
        List<String> anyTerms = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            if (!STOPWORDS.contains(words.get(i))) {
                anyTerms.add(groups.get(i));
            }
        }
        String modelTerm = model == null ? null : modelTerm(model, make);
        String vehicleTsQuery = make == null ? null
                : modelTerm == null ? phrase(make.name()) : phrase(make.name()) + " | " + modelTerm;
        return new ParsedQuery(normalized, words, groups.isEmpty() ? null : String.join(" & ", groups), reference,
                make, model, year, city, anyTerms, vehicleTsQuery, modelTerm);
    }

    /** Nombre normalizado como término de {@code tsquery}: {@code palabra} o {@code (varias & palabras)}. */
    static String phrase(String name) {
        String normalized = normalizeName(name);
        if (normalized == null) {
            return null;
        }
        String[] parts = normalized.split(" ");
        return parts.length == 1 ? parts[0] : "(" + String.join(" & ", parts) + ")";
    }

    /** El modelo en el texto: su nombre si tiene letras; si no («3»), junto a la marca («mazda & 3»). */
    private static String modelTerm(Model model, Make make) {
        String name = normalizeName(model.name());
        if (name == null) {
            return null;
        }
        if (name.chars().anyMatch(Character::isLetter) || make == null) {
            return phrase(name);
        }
        return phrase(make.name() + " " + name);
    }

    /**
     * {@code (palabra | sinónimo | (frase & de & sinónimo)) & … & última:*}. Solo {@code [a-z0-9]} y operadores: no
     * admite sintaxis del usuario.
     */
    static String tsQuery(List<String> words, Map<String, List<String>> synonyms) {
        List<String> groups = groups(words, synonyms);
        return groups.isEmpty() ? null : String.join(" & ", groups);
    }

    /** Un grupo por palabra: la palabra (con prefijo si es la última) o la alternativa con sus sinónimos. */
    private static List<String> groups(List<String> words, Map<String, List<String>> synonyms) {
        List<String> groups = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            boolean last = i == words.size() - 1;
            List<String> alternatives = new ArrayList<>();
            // Sin prefijo en palabras vacías: «para:*» encontraría «parabrisas».
            alternatives.add(last && word.length() >= 3 && !STOPWORDS.contains(word) ? word + ":*" : word);
            for (String expansion : synonyms.getOrDefault(word, List.of())) {
                String[] parts = expansion.split(" ");
                alternatives.add(parts.length == 1 ? parts[0] : "(" + String.join(" & ", parts) + ")");
            }
            groups.add(alternatives.size() == 1 ? alternatives.getFirst() : "(" + String.join(" | ", alternatives) + ")");
        }
        return groups;
    }

    private static Integer findYear(List<String> tokens, boolean[] used) {
        int max = Year.now().getValue() + 1;
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (!used[i] && YEAR.matcher(token).matches()) {
                int value = Integer.parseInt(token);
                if (value >= SearchQuery.MIN_YEAR && value <= max) {
                    used[i] = true;
                    return value;
                }
            }
        }
        return null;
    }

    private static String findReference(List<String> tokens, boolean[] used) {
        for (int i = 0; i < tokens.size(); i++) {
            String candidate = tokens.get(i).toUpperCase(Locale.ROOT);
            if (!used[i] && REFERENCE.matcher(candidate).matches() && HAS_DIGIT.matcher(candidate).matches()) {
                used[i] = true;
                return candidate;
            }
        }
        return null;
    }

    private record Match<T>(T value) {
    }

    /** El n-grama más largo (de {@code max} a {@code min} tokens contiguos libres) que {@code lookup} reconoce. */
    private static <T> Match<T> longest(List<String> tokens, boolean[] used, int min, int max,
            java.util.function.Function<String, T> lookup) {
        for (int n = max; n >= min; n--) {
            for (int start = 0; start + n <= tokens.size(); start++) {
                boolean free = true;
                for (int k = start; k < start + n; k++) {
                    free &= !used[k];
                }
                if (!free) {
                    continue;
                }
                String key = normalizeName(String.join(" ", tokens.subList(start, start + n)));
                T value = key == null ? null : lookup.apply(key);
                if (value != null) {
                    for (int k = start; k < start + n; k++) {
                        used[k] = true;
                    }
                    return new Match<>(value);
                }
            }
        }
        return null;
    }

    private static <T> T single(List<T> candidates) {
        return candidates != null && candidates.size() == 1 ? candidates.getFirst() : null;
    }

    /** Un único candidato o, si hay marca reconocida, el único de esa marca. */
    private static Model pickModel(List<Model> candidates, UUID makeId) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        List<Model> filtered = makeId == null ? candidates
                : candidates.stream().filter(model -> makeId.equals(model.makeId())).toList();
        return filtered.size() == 1 ? filtered.getFirst() : null;
    }

    private static Make closest(String token, Map<String, Make> names) {
        Make found = null;
        for (Map.Entry<String, Make> entry : names.entrySet()) {
            if (Math.abs(entry.getKey().length() - token.length()) <= 1 && distance(token, entry.getKey()) <= 1) {
                if (found != null && !found.id().equals(entry.getValue().id())) {
                    return null;
                }
                found = entry.getValue();
            }
        }
        return found;
    }

    private static List<Model> closestAll(String token, Map<String, List<Model>> names) {
        List<Model> found = new ArrayList<>();
        for (Map.Entry<String, List<Model>> entry : names.entrySet()) {
            if (Math.abs(entry.getKey().length() - token.length()) <= 1 && distance(token, entry.getKey()) <= 1) {
                found.addAll(entry.getValue());
            }
        }
        return found;
    }

    /** Distancia de edición con transposición de letras vecinas (Damerau restringida). */
    static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }

    private Index index() {
        Vocabulary vocabulary = dictionary.vocabulary();
        Index current = index;
        if (current != null && current.source == vocabulary) {
            return current;
        }
        Index built = new Index(vocabulary == null ? Vocabulary.EMPTY : vocabulary);
        index = built;
        return built;
    }

    /** Búsquedas derivadas del vocabulario; se reconstruye cuando cambia la instancia del vocabulario. */
    private static final class Index {
        final Vocabulary source;
        final Map<UUID, Make> makesById = new HashMap<>();
        final Map<String, Make> makesByName = new HashMap<>();
        final Map<String, List<Model>> modelsByName = new HashMap<>();
        final Map<String, List<Model>> modelsByFullName = new HashMap<>();
        final Map<String, Make> fuzzyMakes = new HashMap<>();
        final Map<String, List<Model>> fuzzyModels = new HashMap<>();
        final Map<String, City> citiesByName = new HashMap<>();
        final Map<String, List<String>> synonyms;
        final Set<String> domainWords = new HashSet<>();

        Index(Vocabulary vocabulary) {
            source = vocabulary;
            synonyms = vocabulary.synonyms();
            for (Make make : vocabulary.makes()) {
                String name = normalizeName(make.name());
                if (name == null) {
                    continue;
                }
                makesById.put(make.id(), make);
                makesByName.putIfAbsent(name, make);
                if (!name.contains(" ") && name.length() >= 4) {
                    fuzzyMakes.putIfAbsent(name, make);
                }
            }
            for (Model model : vocabulary.models()) {
                String name = normalizeName(model.name());
                Make make = makesById.get(model.makeId());
                if (name == null || make == null) {
                    continue;
                }
                String full = normalizeName(make.name() + " " + model.name());
                modelsByFullName.computeIfAbsent(full, key -> new ArrayList<>()).add(model);
                // Un modelo solo (sin la marca) debe tener letras: «3» o «626» solo se reconocen como «mazda 3».
                if (name.length() >= 2 && name.chars().anyMatch(Character::isLetter)) {
                    modelsByName.computeIfAbsent(name, key -> new ArrayList<>()).add(model);
                    if (!name.contains(" ") && name.length() >= 4 && LETTERS.matcher(name).matches()) {
                        fuzzyModels.computeIfAbsent(name, key -> new ArrayList<>()).add(model);
                    }
                }
            }
            Comparator<City> preference = Comparator.comparing(City::capital).thenComparingLong(City::population);
            for (City city : vocabulary.cities()) {
                for (String alias : cityAliases(city.name())) {
                    citiesByName.merge(alias, city,
                            (current, candidate) -> preference.compare(candidate, current) > 0 ? candidate : current);
                }
            }
            City bogota = vocabulary.cities().stream()
                    .filter(city -> BOGOTA_CODE.equals(city.code()))
                    .findFirst()
                    .orElse(new City(BOGOTA_CODE, "Bogotá, D.C.", true, 0));
            citiesByName.put("bogota", bogota);
            citiesByName.put("bogota dc", bogota);
            synonyms.forEach((term, expansions) -> {
                domainWords.addAll(List.of(term.split(" ")));
                expansions.forEach(expansion -> domainWords.addAll(List.of(expansion.split(" "))));
            });
        }

        /** Nombre completo y la parte antes de «,» o «(» («Bogotá, D.C.» → «bogota d c» y «bogota»). */
        private static List<String> cityAliases(String name) {
            List<String> aliases = new ArrayList<>();
            String full = normalizeName(name);
            if (full != null) {
                aliases.add(full);
            }
            if (name != null) {
                String head = normalizeName(name.split("[,(]", 2)[0]);
                if (head != null && !head.equals(full)) {
                    aliases.add(head);
                }
            }
            return aliases;
        }
    }
}
