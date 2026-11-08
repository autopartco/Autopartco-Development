package com.autopartco.backend.core.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class StorePublicContactTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    private static Set<String> invalidFields(StorePublicContactRequest request) {
        return VALIDATOR.validate(new StoreUpdateRequest(null, null, null, null, request)).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void acceptsColombianNumbersHttpsAndUsernames() {
        assertThat(invalidFields(new StorePublicContactRequest(
                "+573001234567", "+576015551234", "https://repuestos.co", "repuestos.andes", "RepuestosAndes",
                "repuestos_andes"))).isEmpty();
    }

    @Test
    void emptyStringsAreValidToClearFields() {
        assertThat(invalidFields(new StorePublicContactRequest("", "", "", "", "", ""))).isEmpty();
    }

    @Test
    void rejectsMalformedValues() {
        assertThat(invalidFields(new StorePublicContactRequest(
                "3001234567", "+1 555", "http://repuestos.co", "@andes", "facebook.com/x", "https://tiktok.com/@a")))
                .containsExactlyInAnyOrder("publicProfile.whatsapp", "publicProfile.phone", "publicProfile.website",
                        "publicProfile.instagram", "publicProfile.facebook", "publicProfile.tiktok");
    }

    @Test
    void requestWithOnlyEmptyContactHasNoChanges() {
        assertThat(invalidFields(new StorePublicContactRequest(null, null, null, null, null, null)))
                .contains("changes");
    }

    @Test
    void mergeKeepsNullClearsBlankAndTrims() {
        Map<String, Object> merged = StoreService.mergeContact(
                Map.of("whatsapp", "+573001234567", "instagram", "viejo", "website", "https://a.co"),
                new StorePublicContactRequest(null, null, "", " nuevo ", null, "tt"));

        assertThat(merged).containsExactlyInAnyOrderEntriesOf(Map.of(
                "whatsapp", "+573001234567", "instagram", "nuevo", "tiktok", "tt"));
    }
}
