package com.autopartco.backend.core.payments.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.autopartco.backend.core.payments.WompiEnvironment;
import com.autopartco.backend.core.payments.WompiProperties;
import com.autopartco.backend.core.payments.internal.WompiConfigurationValidator.Inputs;

class WompiConfigurationValidatorTest {

    private static WompiProperties sandbox(String publicKey, String privateKey) {
        return new WompiProperties(WompiEnvironment.SANDBOX, publicKey, privateKey, "integrity", "events",
                "https://checkout.wompi.co/p/", "http://localhost:5173/portal/pago/resultado");
    }

    private static WompiProperties production(String redirectUrl) {
        return new WompiProperties(WompiEnvironment.PRODUCTION, "pub_prod_abc", "prv_prod_abc", "integrity",
                "events", "https://checkout.wompi.co/p/", redirectUrl);
    }

    private static Inputs dev(String provider) {
        return new Inputs(false, true, provider, "http://localhost:5173");
    }

    private static Inputs prod(String provider) {
        return new Inputs(true, false, provider, "https://autopartco.example");
    }

    @Test
    void localDefaultsWithoutWompiAreValid() {
        WompiProperties empty = new WompiProperties(null, "", "", "", "", "https://checkout.wompi.co/p/",
                "http://localhost:5173/portal/pago/resultado");

        assertThat(WompiConfigurationValidator.problems(empty, dev(""))).isEmpty();
        assertThat(empty.environment()).isEqualTo(WompiEnvironment.SANDBOX);
        assertThat(empty.apiUrl()).isEqualTo("https://sandbox.wompi.co/v1");
    }

    @Test
    void sandboxWithTestKeysIsValid() {
        assertThat(WompiConfigurationValidator.problems(sandbox("pub_test_1", "prv_test_1"), dev("WOMPI"))).isEmpty();
    }

    @Test
    void sandboxRejectsProductionKeys() {
        List<String> problems = WompiConfigurationValidator.problems(sandbox("pub_prod_1", "prv_prod_1"), dev(""));

        assertThat(problems).hasSize(2);
        assertThat(problems.get(0)).contains("WOMPI_PUBLIC_KEY", "pub_test_").doesNotContain("pub_prod_1");
        assertThat(problems.get(1)).contains("WOMPI_PRIVATE_KEY", "prv_test_").doesNotContain("prv_prod_1");
    }

    @Test
    void productionRejectsTestKeys() {
        WompiProperties properties = new WompiProperties(WompiEnvironment.PRODUCTION, "pub_test_1", "prv_test_1",
                "integrity", "events", "https://checkout.wompi.co/p/", "https://autopartco.example/portal/pago/resultado");

        assertThat(WompiConfigurationValidator.problems(properties, prod("WOMPI")))
                .anyMatch(problem -> problem.contains("pub_prod_"))
                .anyMatch(problem -> problem.contains("prv_prod_"));
    }

    @Test
    void productionRequiresHttpsRedirectAndFrontend() {
        Inputs httpFrontend = new Inputs(true, false, "WOMPI", "http://autopartco.example");

        assertThat(WompiConfigurationValidator.problems(
                production("http://localhost:5173/portal/pago/resultado"), httpFrontend))
                .anyMatch(problem -> problem.startsWith("WOMPI_REDIRECT_URL must use https://"))
                .anyMatch(problem -> problem.startsWith("APP_FRONTEND_BASE_URL must use https://"));
        assertThat(WompiConfigurationValidator.problems(
                production("https://autopartco.example/portal/pago/resultado"), prod("WOMPI"))).isEmpty();
    }

    @Test
    void prodProfileRejectsSimulators() {
        Inputs billingSimulator = new Inputs(true, true, "WOMPI", "https://autopartco.example");
        WompiProperties properties = production("https://autopartco.example/portal/pago/resultado");

        assertThat(WompiConfigurationValidator.problems(properties, billingSimulator))
                .containsExactly("SIMULATED_PAYMENTS_ENABLED must be false in the prod profile");
        assertThat(WompiConfigurationValidator.problems(properties, prod("SIMULATED")))
                .containsExactly("APP_CHECKOUT_PAYMENT_PROVIDER=SIMULATED is not allowed in the prod profile");
        assertThat(WompiConfigurationValidator.problems(properties, prod("")))
                .containsExactly("APP_CHECKOUT_PAYMENT_PROVIDER is required in the prod profile");
    }

    @Test
    void wompiInUseRequiresEverySecret() {
        WompiProperties missing = new WompiProperties(WompiEnvironment.SANDBOX, "pub_test_1", "", "", " ",
                "https://checkout.wompi.co/p/", "http://localhost:5173/portal/pago/resultado");

        assertThat(WompiConfigurationValidator.problems(missing, dev("WOMPI"))).containsExactly(
                "WOMPI_PRIVATE_KEY is required when Wompi is in use",
                "WOMPI_INTEGRITY_SECRET is required when Wompi is in use",
                "WOMPI_EVENTS_SECRET is required when Wompi is in use");
    }

    @Test
    void unknownProviderIsRejected() {
        assertThat(WompiConfigurationValidator.problems(sandbox("", ""), dev("PAYPAL")))
                .containsExactly("APP_CHECKOUT_PAYMENT_PROVIDER must be SIMULATED or WOMPI");
    }

    @Test
    void constructorFailsStartupWithoutLeakingSecrets() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.billing.simulated-payments-enabled", "true")
                .withProperty("app.checkout.payment-provider", "SIMULATED")
                .withProperty("app.frontend-base-url", "https://autopartco.example");
        environment.setActiveProfiles("prod");
        WompiProperties properties = new WompiProperties(WompiEnvironment.PRODUCTION, "pub_prod_1",
                "prv_test_SECRET", "integrity-SECRET", "events-SECRET", "https://checkout.wompi.co/p/",
                "https://autopartco.example/portal/pago/resultado");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new WompiConfigurationValidator(properties, environment));

        assertThat(error.getMessage())
                .contains("WOMPI_PRIVATE_KEY", "SIMULATED_PAYMENTS_ENABLED", "APP_CHECKOUT_PAYMENT_PROVIDER")
                .doesNotContain("SECRET");
        assertThat(properties.toString()).doesNotContain("SECRET");
    }

    @Test
    void prodProfileRequiresProductionEnvironmentUnlessExplicitOptIn() {
        WompiProperties sandbox = new WompiProperties(WompiEnvironment.SANDBOX, "pub_test_1", "prv_test_1",
                "integrity", "events", "https://checkout.wompi.co/p/",
                "https://autopartco.example/portal/pago/resultado");

        assertThat(WompiConfigurationValidator.problems(sandbox, prod("WOMPI"))).containsExactly(
                "WOMPI_ENVIRONMENT must be PRODUCTION in the prod profile "
                        + "(set WOMPI_ALLOW_SANDBOX_IN_PROD=true to opt in to SANDBOX)");
        Inputs optIn = new Inputs(true, false, "WOMPI", "https://autopartco.example", true, 30);
        assertThat(WompiConfigurationValidator.problems(sandbox, optIn)).isEmpty();
        assertThat(WompiConfigurationValidator.problems(production("https://autopartco.example/r"), prod("WOMPI")))
                .isEmpty();
    }

    @Test
    void sandboxInProdOptInIsReadFromConfigurationAndDefaultsToFalse() {
        WompiProperties sandbox = new WompiProperties(WompiEnvironment.SANDBOX, "pub_test_1", "prv_test_1",
                "integrity", "events", "https://checkout.wompi.co/p/",
                "https://autopartco.example/portal/pago/resultado");
        MockEnvironment withoutOptIn = new MockEnvironment()
                .withProperty("app.checkout.payment-provider", "WOMPI")
                .withProperty("app.frontend-base-url", "https://autopartco.example");
        withoutOptIn.setActiveProfiles("prod");
        MockEnvironment withOptIn = new MockEnvironment()
                .withProperty("app.checkout.payment-provider", "WOMPI")
                .withProperty("app.frontend-base-url", "https://autopartco.example")
                .withProperty("wompi.allow-sandbox-in-prod", "true");
        withOptIn.setActiveProfiles("prod");

        assertThat(assertThrows(IllegalStateException.class,
                () -> new WompiConfigurationValidator(sandbox, withoutOptIn)).getMessage())
                .contains("WOMPI_ENVIRONMENT must be PRODUCTION");
        assertDoesNotThrow(() -> new WompiConfigurationValidator(sandbox, withOptIn));
    }

    @Test
    void reservationMinutesMustBeBetweenFiveAndOneHundredTwenty() {
        WompiProperties properties = sandbox("", "");
        String problem = "APP_CHECKOUT_RESERVATION_MINUTES must be between 5 and 120";

        assertThat(WompiConfigurationValidator.problems(properties,
                new Inputs(false, false, "", "http://localhost:5173", false, 4))).containsExactly(problem);
        assertThat(WompiConfigurationValidator.problems(properties,
                new Inputs(false, false, "", "http://localhost:5173", false, 121))).containsExactly(problem);
        assertThat(WompiConfigurationValidator.problems(properties,
                new Inputs(false, false, "", "http://localhost:5173", false, 5))).isEmpty();
        assertThat(WompiConfigurationValidator.problems(properties,
                new Inputs(false, false, "", "http://localhost:5173", false, 120))).isEmpty();
        MockEnvironment environment = new MockEnvironment().withProperty("app.checkout.reservation-minutes", "0");
        assertThrows(IllegalStateException.class, () -> new WompiConfigurationValidator(properties, environment));
    }

    @Test
    void constructorAcceptsDevDefaults() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.billing.simulated-payments-enabled", "true");

        assertDoesNotThrow(() -> new WompiConfigurationValidator(sandbox("", ""), environment));
    }
}
