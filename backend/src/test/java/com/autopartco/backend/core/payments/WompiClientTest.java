package com.autopartco.backend.core.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.autopartco.backend.shared.security.TokenHashing;
import com.fasterxml.jackson.databind.ObjectMapper;

class WompiClientTest {
    private static final WompiProperties SANDBOX = new WompiProperties(WompiEnvironment.SANDBOX, "pub_test_abc",
            "prv_test_secret", "integrity", "events", "https://checkout.wompi.co/p/", "https://app.example/ok");

    private MockRestServiceServer server;
    private WompiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WompiClient(SANDBOX, new ObjectMapper(), builder);
    }

    @Test
    void integritySignatureIncludesExpirationOnlyWhenPresent() {
        Instant expiresAt = Instant.parse("2026-11-04T16:00:00.123456Z");

        assertThat(client.integritySignature("APM-1", 23_000_000L, "COP", null))
                .isEqualTo(TokenHashing.sha256Hex("APM-123000000COPintegrity"));
        assertThat(client.integritySignature("APM-1", 23_000_000L, "COP", expiresAt))
                .isEqualTo(TokenHashing.sha256Hex("APM-123000000COP2026-11-04T16:00:00.123Zintegrity"));
    }

    @Test
    void checkoutUrlCarriesSignedParametersAndOptionalExpiration() {
        Instant expiresAt = Instant.parse("2026-11-04T16:00:00Z");

        String url = client.checkoutUrl(new WompiCheckoutRequest(
                "APM-1", 23_000_000L, "COP", "https://front.example/pedido/1/resultado", expiresAt));

        assertThat(url)
                .startsWith("https://checkout.wompi.co/p/?public-key=pub_test_abc")
                .contains("currency=COP", "amount-in-cents=23000000", "reference=APM-1",
                        "signature:integrity=" + client.integritySignature("APM-1", 23_000_000L, "COP", expiresAt),
                        "redirect-url=https://front.example/pedido/1/resultado",
                        "expiration-time=2026-11-04T16:00:00.000Z")
                .doesNotContain("prv_test_secret", "integrity&");
    }

    @Test
    void checkoutUrlRequiresConfiguration() {
        WompiClient unconfigured = new WompiClient(
                new WompiProperties(null, "", "", "", "", "", ""), new ObjectMapper(), RestClient.builder());

        assertThrows(IllegalStateException.class, () -> unconfigured.checkoutUrl(
                new WompiCheckoutRequest("APC-1", 100, "COP", "https://x", null)));
    }

    @Test
    void findTransactionUsesPrivateKeyAgainstSandboxApi() {
        server.expect(requestTo("https://sandbox.wompi.co/v1/transactions/1234-1730733600-49201"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer prv_test_secret"))
                .andRespond(withSuccess("""
                        {"data": {"id": "1234-1730733600-49201", "reference": "APC-1", "status": "APPROVED",
                                  "amount_in_cents": 5000000, "currency": "COP"}}
                        """, MediaType.APPLICATION_JSON));

        Optional<WompiTransaction> transaction = client.findTransaction("1234-1730733600-49201");

        assertThat(transaction).hasValueSatisfying(tx -> {
            assertThat(tx.reference()).isEqualTo("APC-1");
            assertThat(tx.approved()).isTrue();
            assertThat(tx.amountInCents()).isEqualTo(5_000_000L);
        });
        server.verify();
    }

    @Test
    void findTransactionReturnsEmptyOn404AndForMalformedIds() {
        server.expect(requestTo("https://sandbox.wompi.co/v1/transactions/unknown-1"))
                .andRespond(withResourceNotFound());

        assertThat(client.findTransaction("unknown-1")).isEmpty();
        assertThat(client.findTransaction("../admin")).isEmpty();
        assertThat(client.findTransaction(null)).isEmpty();
        server.verify();
    }

    @Test
    void findTransactionReportsUnavailableOnServerError() {
        server.expect(requestTo("https://sandbox.wompi.co/v1/transactions/tx-1")).andRespond(withServerError());

        assertThrows(WompiUnavailableException.class, () -> client.findTransaction("tx-1"));
    }

    @Test
    void findTransactionRequiresPrivateKey() {
        WompiClient noKey = new WompiClient(new WompiProperties(WompiEnvironment.SANDBOX, "pub_test_abc", "",
                "integrity", "events", "https://checkout.wompi.co/p/", "https://app.example/ok"),
                new ObjectMapper(), RestClient.builder());

        assertThrows(WompiUnavailableException.class, () -> noKey.findTransaction("tx-1"));
    }

    @Test
    void findTransactionsByReferenceSkipsUnreadableRows() {
        server.expect(requestTo("https://sandbox.wompi.co/v1/transactions?reference=APM-1"))
                .andExpect(header("Authorization", "Bearer prv_test_secret"))
                .andRespond(withSuccess("""
                        {"data": [
                          {"id": "tx-1", "reference": "APM-1", "status": "DECLINED", "amount_in_cents": 100, "currency": "COP"},
                          {"id": "tx-2", "reference": "APM-1", "status": "APPROVED", "amount_in_cents": 100, "currency": "COP"},
                          {"id": "tx-3", "reference": "APM-1"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<WompiTransaction> transactions = client.findTransactionsByReference("APM-1");

        assertThat(transactions).extracting(WompiTransaction::id).containsExactly("tx-1", "tx-2");
        server.verify();
    }

    @Test
    void productionUsesProductionApi() {
        WompiProperties production = new WompiProperties(WompiEnvironment.PRODUCTION, "pub_prod_a", "prv_prod_a",
                "i", "e", "https://checkout.wompi.co/p/", "https://app.example/ok");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer productionServer = MockRestServiceServer.bindTo(builder).build();
        productionServer.expect(requestTo("https://production.wompi.co/v1/transactions/tx-9"))
                .andRespond(withResourceNotFound());

        assertThat(new WompiClient(production, new ObjectMapper(), builder).findTransaction("tx-9")).isEmpty();
        productionServer.verify();
    }
}
