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
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.autopartco.backend.core.analytics.SearchText;
import com.autopartco.backend.core.organization.ServiceCategory;
import com.autopartco.backend.core.search.ParsedQuery.Correction;
import com.autopartco.backend.core.search.SearchDictionary.City;
import com.autopartco.backend.core.search.SearchDictionary.Make;
import com.autopartco.backend.core.search.SearchDictionary.Model;
import com.autopartco.backend.core.search.SearchDictionary.Vocabulary;

/**
 * Interpreta el texto libre del comprador (§1 del plan v2.0.0, «Interpretación de la consulta»):
 * <ol>
 *   <li>Normaliza: minúsculas, sin tildes, espacios simples, máximo {@value #MAX_LENGTH} caracteres.</li>
 *   <li>Referencia: la que sigue a un prefijo ({@code ref}, {@code cod}, {@code #}, «número de parte»…; 3 caracteres o
 *   más con algún dígito) o cualquier {@code [A-Z0-9-]{5,}} con dígito. Los prefijos nunca cuentan como palabra.</li>
 *   <li>Año: {@code 19xx|20xx} (de 1950 al año siguiente), rangos («2010-2015», «2010 a 2015») y años de dos dígitos
 *   («'08», o «08» y «08-13» justo después del vehículo).</li>
 *   <li>Ortografía de las palabras restantes: primero marca o modelo con un error (palabras de 5 letras o más,
 *   transposiciones incluidas), luego {@link SpellCorrector} sobre el léxico del catálogo.</li>
 *   <li>Vehículo sobre el texto ya corregido: nombre completo («mazda 3», con o sin la marca dentro del nombre del
 *   modelo), pegado o sin separadores («mazda3», «cx5», «sparkgt», «hilux2015»), marca y modelo; un modelo sin letras
 *   («3») solo con su marca. Municipios DIVIPOLA por nombre.</li>
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
    /** Prefijos de una referencia («ref 12345», «número de parte 12345»): nunca son palabra del texto. */
    static final Set<String> REFERENCE_PREFIXES = Set.of("ref", "referencia", "cod", "codigo", "no", "nro", "num",
            "numero", "parte");
    /** Palabra que anuncia el año («modelo 2015», «año 08»): se descarta junto con el año. */
    static final Set<String> YEAR_WORDS = Set.of("modelo", "ano");
    /** Conectores de un rango de años («2010 a 2015»). */
    static final Set<String> RANGE_WORDS = Set.of("a", "al", "hasta");
    /** Modelos numéricos escritos en letras tras su marca («mazda tres»). */
    static final Map<String, String> NUMBER_WORDS = Map.of("uno", "1", "dos", "2", "tres", "3", "cuatro", "4",
            "cinco", "5", "seis", "6", "siete", "7", "ocho", "8", "nueve", "9", "diez", "10");

    private static final Pattern YEAR = Pattern.compile("(19|20)\\d{2}");
    private static final Pattern YEAR_RANGE = Pattern.compile("((?:19|20)\\d{2})-((?:19|20)\\d{2})");
    private static final Pattern SHORT_YEAR = Pattern.compile("(\\d{2})(?:-(\\d{2}))?");
    /** Forma original de un año corto: sin puntos ni comas («1.6» no es «16»). */
    private static final Pattern RAW_SHORT_YEAR = Pattern.compile("'?\\d{2}(-'?\\d{2})?");
    private static final Pattern GLUED_YEAR = Pattern.compile("([a-z][a-z0-9-]*?)-?((?:19|20)\\d{2})");
    private static final Pattern REFERENCE = Pattern.compile("[A-Z0-9-]{5,}");
    private static final Pattern PREFIXED_REFERENCE = Pattern.compile("[a-z0-9-]{3,}");
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

    /** {@code interpret=false}: no reconoce ni corrige nada; todo el texto se busca como texto (con sinónimos). */
    public ParsedQuery parse(String raw, boolean interpret) {
        String normalized = SearchText.normalize(raw);
        if (normalized == null) {
            return ParsedQuery.EMPTY;
        }
        if (normalized.length() > MAX_LENGTH) {
            normalized = normalized.substring(0, MAX_LENGTH).strip();
        }
        Index lookup = index();
        Tokens tokens = Tokens.of(normalized);
        Integer yearFrom = null;
        Integer yearTo = null;
        String reference = null;
        Make make = null;
        Model model = null;
        City city = null;
        List<Correction> corrections = new ArrayList<>();
        if (interpret) {
            splitGluedYears(tokens, lookup);
            reference = findPrefixedReference(tokens);
            int[] years = findYears(tokens);
            if (reference == null) {
                reference = findReference(tokens, lookup);
            }
            correctSpelling(tokens, lookup, corrections);

            int vehicleEnd = -1;
            Match<Model> combo = longest(tokens, 1, MAX_NGRAM, key -> single(lookup.modelsByFullName.get(key)));
            if (combo == null) {
                combo = longest(tokens, 1, 3, key -> single(lookup.compactModels.get(compact(key))));
            }
            if (combo != null) {
                model = combo.value();
                make = lookup.makesById.get(model.makeId());
                vehicleEnd = combo.end();
            }
            if (make == null) {
                Match<Make> exactMake = longest(tokens, 1, 3, lookup.makesByName::get);
                if (exactMake != null) {
                    make = exactMake.value();
                    vehicleEnd = exactMake.end();
                }
            }
            if (model == null) {
                UUID makeId = make == null ? null : make.id();
                Match<Model> exactModel = longest(tokens, 1, 3,
                        key -> pickModel(lookup.modelsByName.get(key), makeId));
                if (exactModel == null && makeId != null) {
                    exactModel = longest(tokens, 1, 3,
                            key -> pickModel(lookup.compactModels.get(compact(key)), makeId));
                }
                if (exactModel != null) {
                    model = exactModel.value();
                    make = lookup.makesById.get(model.makeId());
                    vehicleEnd = Math.max(vehicleEnd, exactModel.end());
                }
            }
            if (years == null && vehicleEnd >= 0) {
                years = findShortYearAfter(tokens, vehicleEnd + 1);
            }
            if (years != null) {
                yearFrom = years[0];
                yearTo = years[1];
            }
            Match<City> exactCity = longest(tokens, 1, 3,
                    key -> key.length() >= 4 ? lookup.citiesByName.get(key) : null);
            city = exactCity == null ? null : exactCity.value();
        }
        List<String> words = new ArrayList<>();
        for (int i = 0; i < tokens.size() && words.size() < MAX_WORDS; i++) {
            if (tokens.used[i]) {
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
        // Intención de servicio sobre el texto completo (los términos de servicio no se descuentan del texto buscado).
        ServiceCategory serviceCategory = interpret ? ServiceTerms.intent(normalizeName(normalized)) : null;
        Integer year = yearFrom == null ? null : yearFrom + (yearTo - yearFrom) / 2;
        return new ParsedQuery(normalized, words, groups.isEmpty() ? null : String.join(" & ", groups), reference,
                make, model, year, city, anyTerms, vehicleTsQuery, modelTerm, serviceCategory, yearFrom, yearTo,
                corrections);
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
            for (String expansion : synonymsOf(word, synonyms)) {
                String[] parts = expansion.split(" ");
                alternatives.add(parts.length == 1 ? parts[0] : "(" + String.join(" & ", parts) + ")");
            }
            groups.add(alternatives.size() == 1 ? alternatives.getFirst() : "(" + String.join(" | ", alternatives) + ")");
        }
        return groups;
    }

    /** Sinónimos de la palabra o, si no tiene, de su singular («plumillas» → «plumilla», «faroles» → «farol»). */
    static List<String> synonymsOf(String word, Map<String, List<String>> synonyms) {
        List<String> exact = synonyms.get(word);
        if (exact != null) {
            return exact;
        }
        if (word.length() > 3 && word.endsWith("s")) {
            List<String> singular = synonyms.get(word.substring(0, word.length() - 1));
            if (singular != null) {
                return singular;
            }
            if (word.length() > 4 && word.endsWith("es")) {
                return synonyms.getOrDefault(word.substring(0, word.length() - 2), List.of());
            }
        }
        return List.of();
    }

    // ------------------------------------------------------------------ tokens

    /** Tokens limpios ({@code [a-z0-9-]}), su forma original y si van marcados con {@code #}. */
    private static final class Tokens {
        final List<String> values = new ArrayList<>();
        final List<String> raws = new ArrayList<>();
        final List<Boolean> hashed = new ArrayList<>();
        boolean[] used = new boolean[0];

        static Tokens of(String normalized) {
            Tokens tokens = new Tokens();
            boolean pendingHash = false;
            for (String raw : normalized.split(" ")) {
                String clean = TOKEN_JUNK.matcher(raw).replaceAll("").replaceAll("^-+|-+$", "");
                if (clean.isEmpty()) {
                    pendingHash |= raw.contains("#");
                    continue;
                }
                tokens.add(tokens.size(), clean, raw, pendingHash || raw.startsWith("#"));
                pendingHash = false;
            }
            tokens.used = new boolean[tokens.size()];
            return tokens;
        }

        int size() {
            return values.size();
        }

        String get(int i) {
            return values.get(i);
        }

        boolean free(int i) {
            return i >= 0 && i < size() && !used[i];
        }

        void add(int at, String value, String raw, boolean hash) {
            values.add(at, value);
            raws.add(at, raw);
            hashed.add(at, hash);
        }

        /** Sustituye el token {@code i} por dos (solo antes de marcar nada). */
        void split(int i, String first, String second) {
            values.set(i, first);
            raws.set(i, first);
            add(i + 1, second, second, false);
            used = new boolean[size()];
        }
    }

    /** «hilux2015», «mazda3-2015»: separa el año pegado a un vehículo conocido. */
    private static void splitGluedYears(Tokens tokens, Index lookup) {
        for (int i = 0; i < tokens.size(); i++) {
            Matcher glued = GLUED_YEAR.matcher(tokens.get(i));
            if (glued.matches()) {
                String head = normalizeName(glued.group(1));
                if (head != null && lookup.isVehicle(head)) {
                    tokens.split(i, glued.group(1), glued.group(2));
                    i++;
                }
            }
        }
    }

    // ------------------------------------------------------------------ referencia y año

    /**
     * La referencia que sigue a un prefijo («ref a49457», «cod 1234», «#1234», «número de parte 89643»). Marca como
     * usados todos los prefijos, haya o no referencia: no son palabras del producto.
     */
    private static String findPrefixedReference(Tokens tokens) {
        String reference = null;
        boolean prefixed = false;
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (REFERENCE_PREFIXES.contains(token)) {
                tokens.used[i] = true;
                prefixed = true;
                continue;
            }
            if (prefixed && token.equals("de") && i + 1 < tokens.size()
                    && REFERENCE_PREFIXES.contains(tokens.get(i + 1))) {
                tokens.used[i] = true;
                continue;
            }
            if (reference == null && (prefixed || tokens.hashed.get(i))
                    && PREFIXED_REFERENCE.matcher(token).matches() && HAS_DIGIT.matcher(token).matches()) {
                tokens.used[i] = true;
                reference = token.toUpperCase(Locale.ROOT);
            }
            prefixed = false;
        }
        return reference;
    }

    private static String findReference(Tokens tokens, Index lookup) {
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            String candidate = token.toUpperCase(Locale.ROOT);
            if (!tokens.used[i] && REFERENCE.matcher(candidate).matches() && HAS_DIGIT.matcher(candidate).matches()
                    && shortYears(token) == null && !lookup.isVehicle(normalizeName(token))) {
                tokens.used[i] = true;
                return candidate;
            }
        }
        return null;
    }

    /** {@code [desde, hasta]} del primer año o rango de 4 dígitos, o de un año corto con apóstrofo («'08»). */
    private static int[] findYears(Tokens tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.used[i]) {
                continue;
            }
            String token = tokens.get(i);
            Matcher range = YEAR_RANGE.matcher(token);
            if (range.matches()) {
                int[] years = range(Integer.parseInt(range.group(1)), Integer.parseInt(range.group(2)));
                if (years != null) {
                    tokens.used[i] = true;
                    dropYearWord(tokens, i - 1);
                    return years;
                }
            }
            if (YEAR.matcher(token).matches() && validYear(Integer.parseInt(token))) {
                int from = Integer.parseInt(token);
                tokens.used[i] = true;
                dropYearWord(tokens, i - 1);
                int next = i + 1;
                if (tokens.free(next) && RANGE_WORDS.contains(tokens.get(next))) {
                    next++;
                }
                if (tokens.free(next) && YEAR.matcher(tokens.get(next)).matches()) {
                    int[] years = range(from, Integer.parseInt(tokens.get(next)));
                    if (years != null) {
                        for (int k = i + 1; k <= next; k++) {
                            tokens.used[k] = true;
                        }
                        return years;
                    }
                }
                return new int[] {from, from};
            }
        }
        for (int i = 0; i < tokens.size(); i++) {
            if (!tokens.used[i] && tokens.raws.get(i).startsWith("'")) {
                int[] years = shortYearAt(tokens, i);
                if (years != null) {
                    return years;
                }
            }
        }
        return null;
    }

    /** Año corto («08», «08-13», «08 a 13») en {@code start}, tras «modelo» o «año» opcionales. */
    private static int[] findShortYearAfter(Tokens tokens, int start) {
        int i = start;
        if (tokens.free(i) && YEAR_WORDS.contains(tokens.get(i))) {
            i++;
        }
        int[] years = shortYearAt(tokens, i);
        if (years != null && i > start) {
            tokens.used[start] = true;
        }
        return years;
    }

    private static int[] shortYearAt(Tokens tokens, int i) {
        if (!tokens.free(i) || !RAW_SHORT_YEAR.matcher(tokens.raws.get(i)).matches()) {
            return null;
        }
        int[] years = shortYears(tokens.get(i));
        if (years == null) {
            return null;
        }
        tokens.used[i] = true;
        if (years[0] == years[1]) {
            int next = i + 1;
            if (tokens.free(next) && RANGE_WORDS.contains(tokens.get(next))) {
                next++;
            }
            if (tokens.free(next) && RAW_SHORT_YEAR.matcher(tokens.raws.get(next)).matches()) {
                int[] to = shortYears(tokens.get(next));
                int[] joined = to == null || to[0] != to[1] ? null : range(years[0], to[0]);
                if (joined != null) {
                    for (int k = i + 1; k <= next; k++) {
                        tokens.used[k] = true;
                    }
                    return joined;
                }
            }
        }
        return years;
    }

    /** «08» → 2008–2008, «08-13» → 2008–2013, o {@code null}. */
    private static int[] shortYears(String token) {
        Matcher matcher = SHORT_YEAR.matcher(token);
        if (!matcher.matches()) {
            return null;
        }
        Integer from = expandShortYear(matcher.group(1));
        Integer to = matcher.group(2) == null ? from : expandShortYear(matcher.group(2));
        return from == null || to == null ? null : range(from, to);
    }

    /** {@code 00}–(año siguiente) → 20xx; {@code 50}–{@code 99} → 19xx; lo demás no es año. */
    private static Integer expandShortYear(String digits) {
        int value = Integer.parseInt(digits);
        int max = maxYear();
        if (value <= max % 100) {
            return 2000 + value;
        }
        return 1900 + value >= SearchQuery.MIN_YEAR ? 1900 + value : null;
    }

    private static int[] range(int from, int to) {
        int low = Math.min(from, to);
        int high = Math.max(from, to);
        return validYear(low) && validYear(high) ? new int[] {low, high} : null;
    }

    private static boolean validYear(int value) {
        return value >= SearchQuery.MIN_YEAR && value <= maxYear();
    }

    private static int maxYear() {
        return Year.now().getValue() + 1;
    }

    private static void dropYearWord(Tokens tokens, int i) {
        if (tokens.free(i) && YEAR_WORDS.contains(tokens.get(i))) {
            tokens.used[i] = true;
        }
    }

    // ------------------------------------------------------------------ ortografía

    /**
     * Corrige en su sitio los tokens libres de letras: primero como marca o modelo con un error (la lógica difusa de
     * vehículos), luego con el léxico del catálogo. Un vehículo mal escrito nunca se convierte en otra palabra: si se
     * parece a una marca o modelo, se queda con ese nombre. Las palabras de vehículos, municipios y vacías no se tocan;
     * las de menos de 5 letras no se corrigen hacia un vehículo.
     */
    private static void correctSpelling(Tokens tokens, Index lookup, List<Correction> corrections) {
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (tokens.used[i] || !LETTERS.matcher(token).matches()) {
                continue;
            }
            String number = NUMBER_WORDS.get(token);
            if (number != null) {
                if (i > 0 && lookup.makesByName.containsKey(tokens.get(i - 1))
                        && lookup.modelsByFullName.containsKey(tokens.get(i - 1) + " " + number)) {
                    replace(tokens, i, number, corrections);
                }
                continue;
            }
            if (STOPWORDS.contains(token) || lookup.vehicleWords.contains(token) || lookup.isVehicle(token)
                    || lookup.cityWords.contains(token)) {
                continue;
            }
            String fixed = token.length() >= FUZZY_MIN_LENGTH && !lookup.domainWords.contains(token)
                    ? lookup.vehicleSpelling(token) : null;
            if (fixed == null) {
                fixed = lookup.speller.correct(token)
                        .filter(word -> token.length() >= FUZZY_MIN_LENGTH || !lookup.vehicleWords.contains(word))
                        .orElse(null);
            }
            if (fixed != null) {
                replace(tokens, i, fixed, corrections);
            }
        }
    }

    private static void replace(Tokens tokens, int i, String to, List<Correction> corrections) {
        corrections.add(new Correction(tokens.get(i), to));
        tokens.values.set(i, to);
        tokens.raws.set(i, to);
    }

    // ------------------------------------------------------------------ vehículo

    private record Match<T>(T value, int start, int end) {
    }

    /** El n-grama más largo (de {@code max} a {@code min} tokens contiguos libres) que {@code lookup} reconoce. */
    private static <T> Match<T> longest(Tokens tokens, int min, int max, Function<String, T> lookup) {
        for (int n = max; n >= min; n--) {
            for (int start = 0; start + n <= tokens.size(); start++) {
                boolean free = true;
                for (int k = start; k < start + n; k++) {
                    free &= !tokens.used[k];
                }
                if (!free) {
                    continue;
                }
                String key = normalizeName(String.join(" ", tokens.values.subList(start, start + n)));
                T value = key == null ? null : lookup.apply(key);
                if (value != null) {
                    for (int k = start; k < start + n; k++) {
                        tokens.used[k] = true;
                    }
                    return new Match<>(value, start, start + n - 1);
                }
            }
        }
        return null;
    }

    /** Clave sin separadores («cx 5» → «cx5»); {@code null} si junta palabras vacías («de l200»). */
    private static String compact(String key) {
        String[] parts = key.split(" ");
        if (parts.length > 1) {
            for (String part : parts) {
                if (STOPWORDS.contains(part)) {
                    return null;
                }
            }
        }
        return String.join("", parts);
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
        Vocabulary source = vocabulary == null ? Vocabulary.EMPTY : vocabulary;
        Index current = index;
        if (current != null && current.source == source) {
            return current;
        }
        synchronized (this) {
            current = index;
            if (current != null && current.source == source) {
                return current;
            }
            Index built = new Index(source);
            index = built;
            return built;
        }
    }

    /** Búsquedas derivadas del vocabulario; se reconstruye cuando cambia la instancia del vocabulario. */
    private static final class Index {
        final Vocabulary source;
        final Map<UUID, Make> makesById = new HashMap<>();
        final Map<String, Make> makesByName = new HashMap<>();
        /** Modelo por nombre sin la marca, solo si tiene letras («cx 5», «spark gt», «kicks»). */
        final Map<String, List<Model>> modelsByName = new HashMap<>();
        /** Modelo por «marca + nombre sin la marca» («mazda 3», tanto de «3» como de «Mazda 3»). */
        final Map<String, List<Model>> modelsByFullName = new HashMap<>();
        /** Las dos claves anteriores sin separadores («mazda3», «cx5», «sparkgt»). */
        final Map<String, List<Model>> compactModels = new HashMap<>();
        final Map<String, Make> fuzzyMakes = new HashMap<>();
        final Map<String, List<Model>> fuzzyModels = new HashMap<>();
        final Map<String, City> citiesByName = new HashMap<>();
        final Map<String, List<String>> synonyms;
        final Set<String> domainWords = new HashSet<>();
        /** Palabras de los nombres de marcas y modelos. */
        final Set<String> vehicleWords = new HashSet<>();
        final Set<String> cityWords = new HashSet<>();
        final SpellCorrector speller;

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
                vehicleWords.addAll(List.of(name.split(" ")));
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
                String makeName = normalizeName(make.name());
                // En el catálogo el modelo puede incluir la marca («Mazda 3») o no («3»): las dos formas valen igual.
                String bare = name.startsWith(makeName + " ") ? name.substring(makeName.length() + 1) : name;
                String full = makeName + " " + bare;
                addModel(modelsByFullName, full, model);
                addModel(compactModels, full.replace(" ", ""), model);
                vehicleWords.addAll(List.of(name.split(" ")));
                // Un modelo solo (sin la marca) debe tener letras: «3» o «626» solo se reconocen como «mazda 3».
                if (bare.length() >= 2 && bare.chars().anyMatch(Character::isLetter)) {
                    addModel(modelsByName, bare, model);
                    if (bare.replace(" ", "").length() >= 3) {
                        addModel(compactModels, bare.replace(" ", ""), model);
                    }
                    if (!bare.contains(" ") && bare.length() >= 4 && LETTERS.matcher(bare).matches()) {
                        addModel(fuzzyModels, bare, model);
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
            citiesByName.keySet().forEach(alias -> cityWords.addAll(List.of(alias.split(" "))));
            synonyms.forEach((term, expansions) -> {
                domainWords.addAll(List.of(term.split(" ")));
                expansions.forEach(expansion -> domainWords.addAll(List.of(expansion.split(" "))));
            });
            speller = new SpellCorrector(lexicon(vocabulary));
        }

        /**
         * Léxico del corrector: el del catálogo, más las palabras de marcas, modelos y sinónimos como destinos; las
         * palabras vacías, prefijos de referencia, años, números, municipios y servicios solo como conocidas.
         */
        private Map<String, Integer> lexicon(Vocabulary vocabulary) {
            Map<String, Integer> lexicon = new HashMap<>();
            vocabulary.lexicon().forEach((word, frequency) -> {
                if (word != null && LETTERS.matcher(word).matches()) {
                    lexicon.merge(word, frequency == null ? 0 : frequency, Integer::sum);
                }
            });
            Set<String> targets = new HashSet<>(vehicleWords);
            targets.addAll(domainWords);
            for (String word : targets) {
                if (word.length() >= 3 && LETTERS.matcher(word).matches()) {
                    lexicon.merge(word, 1, Math::max);
                }
            }
            Set<String> known = new HashSet<>(STOPWORDS);
            known.addAll(REFERENCE_PREFIXES);
            known.addAll(YEAR_WORDS);
            known.addAll(RANGE_WORDS);
            known.addAll(NUMBER_WORDS.keySet());
            known.addAll(cityWords);
            ServiceTerms.all().forEach(entry -> entry.terms()
                    .forEach(term -> known.addAll(List.of(term.split(" ")))));
            for (String word : known) {
                if (LETTERS.matcher(word).matches()) {
                    lexicon.putIfAbsent(word, 0);
                }
            }
            return lexicon;
        }

        private static void addModel(Map<String, List<Model>> map, String key, Model model) {
            List<Model> models = map.computeIfAbsent(key, ignored -> new ArrayList<>());
            if (!models.contains(model)) {
                models.add(model);
            }
        }

        /** Nombre normalizado de marca o modelo (con o sin la marca, con o sin separadores). */
        boolean isVehicle(String name) {
            return makesByName.containsKey(name) || modelsByName.containsKey(name)
                    || modelsByFullName.containsKey(name) || compactModels.containsKey(name.replace(" ", ""));
        }

        /**
         * La marca o el modelo (una palabra) a un error de {@code token}, si es uno solo. No corrige una palabra del
         * catálogo más frecuente que ese nombre: sería una palabra real, no una marca mal escrita.
         */
        String vehicleSpelling(String token) {
            String found = null;
            for (String name : fuzzyMakes.keySet()) {
                if (Math.abs(name.length() - token.length()) <= 1 && distance(token, name) <= 1) {
                    if (found != null && !found.equals(name)) {
                        return null;
                    }
                    found = name;
                }
            }
            if (found == null) {
                for (String name : fuzzyModels.keySet()) {
                    if (Math.abs(name.length() - token.length()) <= 1 && distance(token, name) <= 1) {
                        if (found != null && !found.equals(name)) {
                            return null;
                        }
                        found = name;
                    }
                }
            }
            if (found != null && speller.frequency(token) > speller.frequency(found)) {
                return null;
            }
            return found;
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
