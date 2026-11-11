package com.autopartco.backend.operations.metrics;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.advertising.AdEvents;
import com.autopartco.backend.core.analytics.TrafficRecorder;
import com.autopartco.backend.core.analytics.VisibilityRecorder;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.shared.error.TooManyRequestsException;

class TrafficControllerTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void limitsCallsPerIpAndMinute() {
        TrafficController controller = new TrafficController(mock(TrafficRecorder.class), mock(VisibilityRecorder.class),
                mock(AccountLookup.class), mock(AdEvents.class), clock);
        for (int i = 0; i < TrafficController.MAX_PER_MINUTE; i++) {
            controller.acquire("1.1.1.1");
        }
        assertThrows(TooManyRequestsException.class, () -> controller.acquire("1.1.1.1"));
        assertDoesNotThrow(() -> controller.acquire("2.2.2.2"));
    }

    @Test
    void anonymousIdPrefersBodyAndIssuesVisitorCookieWhenMissing() {
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest();
        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();
        String fromCookie = TrafficController.anonymousId(null, request, response);
        org.assertj.core.api.Assertions.assertThat(response.getHeader("Set-Cookie"))
                .startsWith("ap_aid=" + fromCookie).contains("HttpOnly");

        org.springframework.mock.web.MockHttpServletRequest again =
                new org.springframework.mock.web.MockHttpServletRequest();
        again.setCookies(new jakarta.servlet.http.Cookie("ap_aid", fromCookie));
        org.springframework.mock.web.MockHttpServletResponse second =
                new org.springframework.mock.web.MockHttpServletResponse();
        org.assertj.core.api.Assertions.assertThat(TrafficController.anonymousId("body-id", again, second))
                .isEqualTo("body-id");
        org.assertj.core.api.Assertions.assertThat(TrafficController.anonymousId(null, again, second))
                .isEqualTo(fromCookie);
        org.assertj.core.api.Assertions.assertThat(second.getHeader("Set-Cookie")).isNull();
    }
}
