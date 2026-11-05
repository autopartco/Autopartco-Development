package com.autopartco.backend.core.payments.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.autopartco.backend.core.payments.WompiEnvironment;
import com.autopartco.backend.core.payments.WompiEventOutcome;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class PaymentProviderEventLogTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PaymentProviderEventLog log = new PaymentProviderEventLog(mock(JdbcClient.class), mapper);

    private PaymentProviderEventLog.Entry entry(ObjectNode payload) {
        return new PaymentProviderEventLog.Entry(WompiEnvironment.SANDBOX, "transaction.updated", "abc", "APC-1",
                "tx-1", "APPROVED", 100L, "COP", WompiEventSource.WEBHOOK, WompiEventOutcome.APPLIED, payload);
    }

    @Test
    void insertIsAppendOnlyAndDeduplicatesByChecksum() {
        assertThat(PaymentProviderEventLog.INSERT_SQL)
                .startsWith("INSERT INTO payment_provider_events")
                .contains("ON CONFLICT (provider, checksum) WHERE checksum IS NOT NULL DO NOTHING")
                .doesNotContainIgnoringCase("UPDATE ");
    }

    @Test
    void keepsSmallPayloadsAndReducesOversizedOnes() throws Exception {
        ObjectNode small = mapper.createObjectNode().put("event", "transaction.updated");
        ObjectNode big = mapper.createObjectNode().put("blob", "x".repeat(PaymentProviderEventLog.MAX_PAYLOAD_BYTES));

        assertThat(log.payloadJson(entry(small))).isEqualTo("{\"event\":\"transaction.updated\"}");
        String reduced = log.payloadJson(entry(big));
        assertThat(reduced).contains("\"truncated\":true", "\"reference\":\"APC-1\"").doesNotContain("xxxx");
        assertThat(log.payloadJson(entry(null))).isEqualTo("{}");
    }
}
