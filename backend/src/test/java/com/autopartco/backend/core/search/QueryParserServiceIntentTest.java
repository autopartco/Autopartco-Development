package com.autopartco.backend.core.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.autopartco.backend.core.organization.ServiceCategory;

/** Intención de servicio de {@link QueryParser} y vocabulario de {@link ServiceTerms}. */
class QueryParserServiceIntentTest {
    private final SearchDictionary dictionary = mock(SearchDictionary.class);
    private QueryParser parser;

    @BeforeEach
    void setUp() {
        when(dictionary.vocabulary()).thenReturn(QueryParserTest.vocabulary());
        parser = new QueryParser(dictionary);
    }

    @ParameterizedTest
    @CsvSource({
            "alineacion,ALIGNMENT_BALANCING",
            "Alineación y balanceo,ALIGNMENT_BALANCING",
            "balanceo,ALIGNMENT_BALANCING",
            "alinear,ALIGNMENT_BALANCING",
            "cambio de aceite,LUBRICATION",
            "lubricación,LUBRICATION",
            "aceite y filtro,LUBRICATION",
            "escáner,DIAGNOSTICS",
            "diagnostico,DIAGNOSTICS",
            "revision,DIAGNOSTICS",
            "aire acondicionado,AC",
            "carga de gas,AC",
            "instalacion,INSTALLATION",
            "montaje,INSTALLATION",
            "montallantas,TIRES",
            "despinche,TIRES",
            "electricista,ELECTRICAL",
            "electrico automotriz,ELECTRICAL",
            "latonería,BODY_PAINT",
            "pintura,BODY_PAINT",
            "latoneria y pintura,BODY_PAINT",
            "mecanica,MECHANICS",
            "mecánico,MECHANICS",
            "taller,MECHANICS"
    })
    void triggerTermsActivateTheirCategory(String text, ServiceCategory expected) {
        assertThat(parser.parse(text, true).serviceCategory()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"llanta", "llantas", "pastillas de freno", "aceite", "filtro de aceite", "gas", "bateria"})
    void productWordsDoNotActivateAServiceIntent(String text) {
        assertThat(parser.parse(text, true).serviceCategory()).isNull();
    }

    @Test
    void theLongestTermWinsAndWholeWordsAreRequired() {
        // «latoneria y pintura» (19) frente a «pintura» (7): misma categoría; «aceite y filtro» frente a «aceite».
        assertThat(parser.parse("cambio de aceite y filtro", true).serviceCategory())
                .isEqualTo(ServiceCategory.LUBRICATION);
        // «montaje» no está dentro de «montajes»: solo palabras completas.
        assertThat(parser.parse("montajes", true).serviceCategory()).isNull();
    }

    @Test
    void alignmentInBogotaKeepsTheCityAndTheRemainingText() {
        ParsedQuery parsed = parser.parse("alineacion bogota", true);

        assertThat(parsed.serviceCategory()).isEqualTo(ServiceCategory.ALIGNMENT_BALANCING);
        assertThat(parsed.city().code()).isEqualTo("11001");
        assertThat(parsed.text()).isEqualTo("alineacion");
        SearchInterpretation interpretation = parsed.interpretation();
        assertThat(interpretation.serviceCategory()).isEqualTo(ServiceCategory.ALIGNMENT_BALANCING);
        assertThat(interpretation.cityCode()).isEqualTo("11001");
    }

    @Test
    void aServiceIntentAloneIsAnInterpretationAndCoexistsWithAVehicle() {
        SearchInterpretation alone = parser.parse("cambio de aceite", true).interpretation();
        assertThat(alone).isNotNull();
        assertThat(alone.serviceCategory()).isEqualTo(ServiceCategory.LUBRICATION);
        assertThat(alone.makeId()).isNull();

        ParsedQuery withVehicle = parser.parse("cambio de aceite mazda 3", true);
        assertThat(withVehicle.serviceCategory()).isEqualTo(ServiceCategory.LUBRICATION);
        assertThat(withVehicle.make()).isEqualTo(QueryParserTest.MAZDA);
    }

    @Test
    void withoutInterpretationThereIsNoServiceIntent() {
        ParsedQuery parsed = parser.parse("cambio de aceite", false);

        assertThat(parsed.serviceCategory()).isNull();
        assertThat(parsed.interpretation()).isNull();
        assertThat(parser.parse(null, true).serviceCategory()).isNull();
    }

    @Test
    void everyCategoryHasLabelIconAndOtherHasNoTerms() {
        for (ServiceCategory category : ServiceCategory.values()) {
            ServiceTerms.Entry entry = ServiceTerms.of(category);
            assertThat(entry.label()).isNotBlank();
            assertThat(entry.icon()).matches("[a-z0-9-]+");
            entry.terms().forEach(term -> assertThat(term).isEqualTo(QueryParser.normalizeName(term)));
        }
        assertThat(ServiceTerms.of(ServiceCategory.OTHER).terms()).isEmpty();
        assertThat(ServiceTerms.indexText(ServiceCategory.ALIGNMENT_BALANCING))
                .contains("Alineación y balanceo", "alineacion", "balanceo", "alinear");
    }

    @Test
    void suggestsAtMostTwoCategoriesByPrefixOrIntent() {
        assertThat(ServiceTerms.suggest("alin", 2)).containsExactly(ServiceCategory.ALIGNMENT_BALANCING);
        assertThat(ServiceTerms.suggest("cambio de aceite", 2)).containsExactly(ServiceCategory.LUBRICATION);
        assertThat(ServiceTerms.suggest("a", 2)).isEmpty();
        assertThat(ServiceTerms.suggest("ac", 2)).isEmpty();
        assertThat(ServiceTerms.suggest("mec", 2)).containsExactly(ServiceCategory.MECHANICS);
        assertThat(ServiceTerms.suggest("ele", 1)).hasSizeLessThanOrEqualTo(1);
    }
}
