package com.autopartco.backend.core.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.search.SearchDictionary.City;
import com.autopartco.backend.core.search.SearchDictionary.Make;
import com.autopartco.backend.core.search.SearchDictionary.Model;
import com.autopartco.backend.core.search.SearchDictionary.Vocabulary;

class QueryParserTest {
    static final Make CHEVROLET = new Make(UUID.randomUUID(), "Chevrolet");
    static final Make MAZDA = new Make(UUID.randomUUID(), "Mazda");
    static final Make RENAULT = new Make(UUID.randomUUID(), "Renault");
    static final Make SUZUKI = new Make(UUID.randomUUID(), "Suzuki");
    static final Model SPARK = new Model(UUID.randomUUID(), CHEVROLET.id(), "Spark");
    static final Model SPARK_GT = new Model(UUID.randomUUID(), CHEVROLET.id(), "Spark GT");
    static final Model MAZDA_3 = new Model(UUID.randomUUID(), MAZDA.id(), "3");
    static final Model LOGAN = new Model(UUID.randomUUID(), RENAULT.id(), "Logan");
    static final Model VITARA = new Model(UUID.randomUUID(), SUZUKI.id(), "Vitara");
    static final City BOGOTA = new City("11001", "Bogotá, D.C.", true, 7_900_000);
    static final City MEDELLIN = new City("05001", "Medellín", true, 2_600_000);
    static final City SMALL_LA_UNION = new City("05400", "La Unión", false, 20_000);
    static final City BIG_LA_UNION = new City("52399", "La Unión", false, 30_000);

    static Vocabulary vocabulary() {
        return new Vocabulary(List.of(CHEVROLET, MAZDA, RENAULT, SUZUKI),
                List.of(SPARK, SPARK_GT, MAZDA_3, LOGAN, VITARA),
                List.of(BOGOTA, MEDELLIN, SMALL_LA_UNION, BIG_LA_UNION),
                Map.of("bandas", List.of("pastillas"), "amortiguador", List.of("shock absorber")));
    }

    private final SearchDictionary dictionary = mock(SearchDictionary.class);
    private QueryParser parser;

    @BeforeEach
    void setUp() {
        when(dictionary.vocabulary()).thenReturn(vocabulary());
        parser = new QueryParser(dictionary);
    }

    @Test
    void recognizesModelYearAndCityAndKeepsTheRemainingText() {
        ParsedQuery parsed = parser.parse("pastillas freno spark 2015 bogota", true);

        assertThat(parsed.text()).isEqualTo("pastillas freno");
        assertThat(parsed.model()).isEqualTo(SPARK);
        assertThat(parsed.make()).isEqualTo(CHEVROLET);
        assertThat(parsed.year()).isEqualTo(2015);
        assertThat(parsed.city().code()).isEqualTo("11001");
        assertThat(parsed.tsQuery()).isEqualTo("pastillas & freno:*");
        SearchInterpretation interpretation = parsed.interpretation();
        assertThat(interpretation.text()).isEqualTo("pastillas freno");
        assertThat(interpretation.modelId()).isEqualTo(SPARK.id());
        assertThat(interpretation.makeId()).isEqualTo(CHEVROLET.id());
        assertThat(interpretation.cityCode()).isEqualTo("11001");
        assertThat(interpretation.year()).isEqualTo(2015);
    }

    @Test
    void misspelledMakeWithOneTranspositionIsRecognized() {
        ParsedQuery parsed = parser.parse("chevrolte", true);

        assertThat(parsed.make()).isEqualTo(CHEVROLET);
        assertThat(parsed.model()).isNull();
        assertThat(parsed.text()).isNull();
        assertThat(parsed.tsQuery()).isNull();
        assertThat(QueryParser.distance("chevrolte", "chevrolet")).isEqualTo(1);
    }

    @Test
    void accentsCaseAndSpacesAreNormalized() {
        ParsedQuery parsed = parser.parse("  BUJÍA   Medellín ", true);

        assertThat(parsed.text()).isEqualTo("bujia");
        assertThat(parsed.city()).isEqualTo(MEDELLIN);
    }

    @Test
    void numericModelNeedsItsMakeAndLongestNameWins() {
        assertThat(parser.parse("filtro mazda 3", true).model()).isEqualTo(MAZDA_3);
        assertThat(parser.parse("filtro 3", true).model()).isNull();
        assertThat(parser.parse("spark gt", true).model()).isEqualTo(SPARK_GT);
    }

    @Test
    void shortWordsAreNotFuzzyMatchedAndAmbiguousCitiesPreferTheLargest() {
        assertThat(parser.parse("logn", true).model()).isNull();
        assertThat(parser.parse("logann", true).model()).isEqualTo(LOGAN);
        assertThat(parser.parse("la union", true).city()).isEqualTo(BIG_LA_UNION);
    }

    @Test
    void referenceIsDetectedAndRemovedFromTheText() {
        ParsedQuery parsed = parser.parse("bujia BKR6E-11", true);

        assertThat(parsed.reference()).isEqualTo("BKR6E-11");
        assertThat(parsed.text()).isEqualTo("bujia");
        assertThat(parser.parse("frenos", true).reference()).isNull();
    }

    @Test
    void synonymsExpandAndOnlyTheLastWordIsAPrefix() {
        assertThat(parser.parse("bandas", true).tsQuery()).isEqualTo("(bandas:* | pastillas)");
        assertThat(parser.parse("amortiguador trasero", true).tsQuery())
                .isEqualTo("(amortiguador | (shock & absorber)) & trasero:*");
        assertThat(parser.parse("filtro de", true).tsQuery()).isEqualTo("filtro & de");
    }

    @Test
    void userSyntaxNeverReachesTheTsQuery() {
        ParsedQuery parsed = parser.parse("freno' | !(x) & <-> :*", true);

        assertThat(parsed.tsQuery()).matches("[a-z0-9 &|():*]+");
        assertThat(parsed.words()).containsExactly("freno", "x");
    }

    @Test
    void withoutInterpretationEverythingIsText() {
        ParsedQuery parsed = parser.parse("pastillas spark 2015 bogota", false);

        assertThat(parsed.make()).isNull();
        assertThat(parsed.model()).isNull();
        assertThat(parsed.year()).isNull();
        assertThat(parsed.city()).isNull();
        assertThat(parsed.text()).isEqualTo("pastillas spark 2015 bogota");
        assertThat(parsed.interpretation()).isNull();
    }

    @Test
    void queryIsCappedAt120CharactersAndBlankIsEmpty() {
        ParsedQuery parsed = parser.parse("a".repeat(300), true);

        assertThat(parsed.normalized()).hasSize(QueryParser.MAX_LENGTH);
        assertThat(parser.parse("   ", true)).isEqualTo(ParsedQuery.EMPTY);
        assertThat(parser.parse(null, true).hasText()).isFalse();
    }

    @Test
    void freeTextWithAMakeKeepsTheVehicleAsATextAlternativeAndSkipsStopwords() {
        ParsedQuery parsed = parser.parse("bomba de freno para suzuki", true);

        assertThat(parsed.make()).isEqualTo(SUZUKI);
        assertThat(parsed.model()).isNull();
        assertThat(parsed.text()).isEqualTo("bomba de freno para");
        // «para» es palabra vacía: sin prefijo (no debe encontrar «parabrisas»).
        assertThat(parsed.tsQuery()).isEqualTo("bomba & de & freno & para");
        assertThat(parsed.anyTerms()).containsExactly("bomba", "freno");
        assertThat(parsed.anyTsQuery()).isEqualTo("bomba | freno");
        assertThat(parsed.vehicleTsQuery()).isEqualTo("suzuki");
        assertThat(parsed.modelTsQuery()).isNull();
        assertThat(parsed.interpretation().makeId()).isEqualTo(SUZUKI.id());
    }

    @Test
    void modelTermsGoWithTheMakeWhenTheModelHasNoLetters() {
        ParsedQuery vitara = parser.parse("bomba freno vitara", true);
        assertThat(vitara.vehicleTsQuery()).isEqualTo("suzuki | vitara");
        assertThat(vitara.modelTsQuery()).isEqualTo("vitara");

        ParsedQuery mazda = parser.parse("filtro mazda 3", true);
        assertThat(mazda.vehicleTsQuery()).isEqualTo("mazda | (mazda & 3)");
        assertThat(mazda.modelTsQuery()).isEqualTo("(mazda & 3)");
        assertThat(mazda.anyTsQuery()).isNull();
    }

    @Test
    void yearsOutsideTheRangeStayAsText() {
        ParsedQuery parsed = parser.parse("bomba 1890", true);

        assertThat(parsed.year()).isNull();
        assertThat(parsed.text()).isEqualTo("bomba 1890");
    }

    // ------------------------------------------------------------------ T3: ortografía, vehículo, año, referencia

    static final Make TOYOTA = new Make(UUID.randomUUID(), "Toyota");
    static final Model HILUX = new Model(UUID.randomUUID(), TOYOTA.id(), "Hilux");
    /** Como en producción: el nombre del modelo incluye la marca. */
    static final Model MAZDA_3_FULL = new Model(UUID.randomUUID(), MAZDA.id(), "Mazda 3");
    static final Model MAZDA_2_FULL = new Model(UUID.randomUUID(), MAZDA.id(), "Mazda 2");
    static final Model CX_5 = new Model(UUID.randomUUID(), MAZDA.id(), "CX-5");

    /** Catálogo con léxico de palabras de los listados (frecuencia = listados que la contienen). */
    static Vocabulary catalog() {
        Map<String, Integer> lexicon = new java.util.HashMap<>();
        for (Object[] entry : new Object[][] {{"radiador", 40}, {"valeo", 12}, {"amortiguador", 50},
                {"delantero", 30}, {"pastillas", 60}, {"pastilla", 20}, {"freno", 80}, {"bomba", 70}, {"agua", 40},
                {"filtro", 90}, {"aire", 50}, {"terminal", 15}, {"direccion", 20}, {"aceite", 40}, {"litros", 10},
                {"gasolina", 10}, {"tensor", 1}, {"sensor", 100}, {"correa", 30}, {"bujia", 30}, {"rin", 5}}) {
            lexicon.put((String) entry[0], (Integer) entry[1]);
        }
        return new Vocabulary(List.of(CHEVROLET, MAZDA, TOYOTA),
                List.of(SPARK, SPARK_GT, MAZDA_3_FULL, MAZDA_2_FULL, CX_5, HILUX),
                List.of(BOGOTA, MEDELLIN),
                Map.of("bandas", List.of("pastillas")), lexicon);
    }

    private ParsedQuery catalogParse(String q) {
        SearchDictionary catalog = mock(SearchDictionary.class);
        when(catalog.vocabulary()).thenReturn(catalog());
        return new QueryParser(catalog).parse(q, true);
    }

    private static List<ParsedQuery.Correction> corrections(String... pairs) {
        List<ParsedQuery.Correction> out = new java.util.ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.add(new ParsedQuery.Correction(pairs[i], pairs[i + 1]));
        }
        return out;
    }

    @Test
    void misspelledCatalogWordsAreCorrectedAndTheTextUsesTheCorrection() {
        ParsedQuery radiador = catalogParse("radidor valeo");
        assertThat(radiador.corrections()).isEqualTo(corrections("radidor", "radiador"));
        assertThat(radiador.words()).containsExactly("radiador", "valeo");
        assertThat(radiador.fuzzyText()).isEqualTo("radiador valeo");
        assertThat(radiador.tsQuery()).isEqualTo("radiador & valeo:*");

        ParsedQuery pastillas = catalogParse("pastiyas de freno");
        assertThat(pastillas.corrections()).isEqualTo(corrections("pastiyas", "pastillas"));
        assertThat(pastillas.words()).containsExactly("pastillas", "de", "freno");
    }

    @Test
    void correctionAndVehicleWorkTogether() {
        ParsedQuery parsed = catalogParse("amortigudor delantero hilux");

        assertThat(parsed.corrections()).isEqualTo(corrections("amortigudor", "amortiguador"));
        assertThat(parsed.make()).isEqualTo(TOYOTA);
        assertThat(parsed.model()).isEqualTo(HILUX);
        assertThat(parsed.words()).containsExactly("amortiguador", "delantero");
    }

    @Test
    void numericModelAfterACorrectedMakeInBothCatalogForms() {
        ParsedQuery full = catalogParse("bomba de agua del masda 3");
        assertThat(full.make()).isEqualTo(MAZDA);
        assertThat(full.model()).isEqualTo(MAZDA_3_FULL);
        assertThat(full.words()).containsExactly("bomba", "de", "agua", "del").doesNotContain("3", "masda");
        assertThat(full.corrections()).isEqualTo(corrections("masda", "mazda"));

        ParsedQuery bare = parser.parse("bomba de agua del masda 3", true);
        assertThat(bare.model()).isEqualTo(MAZDA_3);
        assertThat(bare.words()).doesNotContain("3");

        assertThat(catalogParse("bomba mazda tres").model()).isEqualTo(MAZDA_3_FULL);
    }

    @Test
    void gluedAndUnseparatedModelsAreRecognized() {
        ParsedQuery mazda3 = catalogParse("filtro aire mazda3");
        assertThat(mazda3.model()).isEqualTo(MAZDA_3_FULL);
        assertThat(mazda3.words()).containsExactly("filtro", "aire");
        assertThat(parser.parse("filtro aire mazda3", true).model()).as("modelo «3» sin la marca").isEqualTo(MAZDA_3);
        assertThat(catalogParse("filtro mazda-3").model()).isEqualTo(MAZDA_3_FULL);

        ParsedQuery cx5 = catalogParse("terminal direccion cx5");
        assertThat(cx5.model()).isEqualTo(CX_5);
        assertThat(cx5.make()).isEqualTo(MAZDA);
        assertThat(cx5.words()).containsExactly("terminal", "direccion");
        assertThat(catalogParse("terminal cx-5").model()).isEqualTo(CX_5);
        assertThat(catalogParse("terminal mazda cx5").model()).isEqualTo(CX_5);

        assertThat(catalogParse("bujia sparkgt").model()).isEqualTo(SPARK_GT);

        ParsedQuery hilux = catalogParse("amortiguador hilux2015");
        assertThat(hilux.model()).isEqualTo(HILUX);
        assertThat(hilux.year()).isEqualTo(2015);
        assertThat(hilux.words()).containsExactly("amortiguador");
    }

    @Test
    void aLoneNumberIsNeverAModel() {
        assertThat(catalogParse("filtro 3").model()).isNull();
        assertThat(catalogParse("filtro 3").words()).containsExactly("filtro", "3");
    }

    @Test
    void yearRangesAndShortYearsNextToTheVehicle() {
        ParsedQuery range = catalogParse("spark gt 2010-2015");
        assertThat(range.model()).isEqualTo(SPARK_GT);
        assertThat(range.yearFrom()).isEqualTo(2010);
        assertThat(range.yearTo()).isEqualTo(2015);
        assertThat(range.year()).as("año medio como pista").isEqualTo(2012);
        assertThat(range.words()).isEmpty();

        ParsedQuery words = catalogParse("bomba spark 2010 a 2015");
        assertThat(words.yearFrom()).isEqualTo(2010);
        assertThat(words.yearTo()).isEqualTo(2015);
        assertThat(words.words()).containsExactly("bomba");

        ParsedQuery shortRange = catalogParse("bomba de gasolina mazda 2 08-13");
        assertThat(shortRange.model()).isEqualTo(MAZDA_2_FULL);
        assertThat(shortRange.yearFrom()).isEqualTo(2008);
        assertThat(shortRange.yearTo()).isEqualTo(2013);
        assertThat(shortRange.reference()).isNull();
        assertThat(shortRange.words()).containsExactly("bomba", "de", "gasolina");

        ParsedQuery shortYear = catalogParse("bomba spark 08");
        assertThat(shortYear.year()).isEqualTo(2008);
        assertThat(shortYear.words()).containsExactly("bomba");
        assertThat(catalogParse("bomba spark modelo 98").year()).isEqualTo(1998);
        assertThat(catalogParse("bomba spark modelo 98").words()).containsExactly("bomba");
        assertThat(catalogParse("bomba '08 spark").year()).isEqualTo(2008);

        ParsedQuery single = catalogParse("bomba spark 2015");
        assertThat(single.yearFrom()).isEqualTo(2015);
        assertThat(single.yearTo()).isEqualTo(2015);
        assertThat(single.year()).isEqualTo(2015);
    }

    @Test
    void numbersThatAreNotYearsAreLeftAlone() {
        ParsedQuery oil = catalogParse("aceite 5w30 4 litros");
        assertThat(oil.corrections()).isEmpty();
        assertThat(oil.year()).isNull();
        assertThat(oil.reference()).isNull();
        assertThat(oil.words()).containsExactly("aceite", "5w30", "4", "litros");

        assertThat(catalogParse("rin 15 bomba").year()).as("dos dígitos lejos del vehículo").isNull();
        assertThat(catalogParse("filtro mazda 3 1.6").year()).as("cilindrada").isNull();
        assertThat(catalogParse("aceite 500 ml").year()).isNull();
    }

    @Test
    void referencePrefixesAreNeverWords() {
        ParsedQuery prefixed = catalogParse("ref a49457-rna-e50");
        assertThat(prefixed.reference()).isEqualTo("A49457-RNA-E50");
        assertThat(prefixed.words()).isEmpty();
        assertThat(prefixed.tsQuery()).isNull();

        ParsedQuery withPart = catalogParse("bomba M89697-SAA-A14");
        assertThat(withPart.reference()).isEqualTo("M89697-SAA-A14");
        assertThat(withPart.words()).containsExactly("bomba");

        assertThat(catalogParse("codigo 4521 bomba").reference()).isEqualTo("4521");
        assertThat(catalogParse("codigo 4521 bomba").words()).containsExactly("bomba");
        assertThat(catalogParse("bomba #4521").reference()).isEqualTo("4521");
        assertThat(catalogParse("numero de parte 89643").reference()).isEqualTo("89643");
        assertThat(catalogParse("numero de parte 89643").words()).isEmpty();
        assertThat(catalogParse("no. 89643 bomba").words()).containsExactly("bomba");
        assertThat(catalogParse("ref bomba").words()).as("sin referencia").containsExactly("bomba");
    }

    @Test
    void validRareWordsAreNotCorrectedAndShortTokensNeverBecomeAVehicle() {
        ParsedQuery rare = catalogParse("tensor correa");
        assertThat(rare.corrections()).isEmpty();
        assertThat(rare.words()).containsExactly("tensor", "correa");

        assertThat(catalogParse("bomba medellin").corrections()).as("municipio").isEmpty();
        assertThat(parser.parse("logn", true).corrections()).isEmpty();
    }

    @Test
    void withoutInterpretationNothingIsCorrected() {
        SearchDictionary catalog = mock(SearchDictionary.class);
        when(catalog.vocabulary()).thenReturn(catalog());
        ParsedQuery parsed = new QueryParser(catalog).parse("radidor masda 3 2015", false);

        assertThat(parsed.corrections()).isEmpty();
        assertThat(parsed.words()).containsExactly("radidor", "masda", "3", "2015");
        assertThat(parsed.make()).isNull();
        assertThat(parsed.year()).isNull();
    }
}
