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
}
