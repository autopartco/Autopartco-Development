package com.autopartco.backend.operations.metrics;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.analytics.TrafficRecorder;
import com.autopartco.backend.core.analytics.VisibilityRecorder;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.shared.error.TooManyRequestsException;

class TrafficControllerTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void limitsCallsPerIpAndMinute() {
        TrafficController controller = new TrafficController(mock(TrafficRecorder.class), mock(VisibilityRecorder.class),
                mock(AccountLookup.class), clock);
        for (int i = 0; i < TrafficController.MAX_PER_MINUTE; i++) {
            controller.acquire("1.1.1.1");
        }
        assertThrows(TooManyRequestsException.class, () -> controller.acquire("1.1.1.1"));
        assertDoesNotThrow(() -> controller.acquire("2.2.2.2"));
    }
}
