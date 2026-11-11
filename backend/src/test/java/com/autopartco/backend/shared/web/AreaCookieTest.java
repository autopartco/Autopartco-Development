package com.autopartco.backend.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;

/** Cookies funcionales {@code ap_area} (lectura) y {@code ap_aid} (emisión y lectura). */
class AreaCookieTest {

    @Test
    void decodesUrlEncodedValueWithEmptyCity() {
        Optional<AreaCookie.Area> area = AreaCookie.parse("4.60%2C-74.10%2C");
        assertThat(area).isPresent();
        assertThat(area.get().lat()).isEqualByComparingTo("4.60");
        assertThat(area.get().lng()).isEqualByComparingTo("-74.10");
        assertThat(area.get().cityCode()).isNull();
    }

    @Test
    void readsCityAndRoundsToHundredths() {
        AreaCookie.Area area = AreaCookie.parse("6.2442%2C-75.5812%2C05001").orElseThrow();
        assertThat(area.lat()).isEqualByComparingTo("6.24");
        assertThat(area.lng()).isEqualByComparingTo("-75.58");
        assertThat(area.cityCode()).isEqualTo("05001");
        assertThat(AreaCookie.parse("4.6,-74.1")).isPresent();
    }

    @Test
    void ignoresInvalidValuesSilently() {
        assertThat(AreaCookie.parse(null)).isEmpty();
        assertThat(AreaCookie.parse("")).isEmpty();
        assertThat(AreaCookie.parse("basura")).isEmpty();
        assertThat(AreaCookie.parse("4.60")).isEmpty();
        assertThat(AreaCookie.parse("40.71%2C-74.00%2C")).isEmpty(); // Nueva York: fuera de Colombia
        assertThat(AreaCookie.parse("4.60%2C-74.10%2Cabc")).isEmpty();
        assertThat(AreaCookie.parse("4.60%2C-74.10%2C11001%2Cx")).isEmpty();
        assertThat(AreaCookie.parse("1e3%2C-74.10")).isEmpty();
        assertThat(AreaCookie.parse("%E0%A4%A")).isEmpty(); // codificación rota
    }

    @Test
    void readsFromRequestCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("otra", "x"), new Cookie(AreaCookie.NAME, "4.71%2C-74.07%2C11001"));
        assertThat(AreaCookie.read(request)).map(AreaCookie.Area::cityCode).contains("11001");
        assertThat(AreaCookie.read(new MockHttpServletRequest())).isEmpty();
    }

    @Test
    void visitorCookieIsCrossSiteSecureInProductionAndLaxOnLocalhost() {
        ResponseCookie remote = VisitorCookie.issue(UUID.randomUUID(), false);
        assertThat(remote.isHttpOnly()).isTrue();
        assertThat(remote.isSecure()).isTrue();
        assertThat(remote.getSameSite()).isEqualTo("None");
        assertThat(remote.getMaxAge().toDays()).isEqualTo(365);
        assertThat(remote.getPath()).isEqualTo("/");

        ResponseCookie local = VisitorCookie.issue(UUID.randomUUID(), true);
        assertThat(local.isHttpOnly()).isTrue();
        assertThat(local.isSecure()).isFalse();
        assertThat(local.getSameSite()).isEqualTo("Lax");
    }

    @Test
    void ensureIssuesOnlyWhenMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("api.autopartco.co");
        MockHttpServletResponse response = new MockHttpServletResponse();
        UUID issued = VisitorCookie.ensure(request, response);
        String header = response.getHeader("Set-Cookie");
        assertThat(header).contains("ap_aid=" + issued).contains("HttpOnly").contains("Secure")
                .contains("SameSite=None");

        MockHttpServletRequest again = new MockHttpServletRequest();
        again.setCookies(new Cookie(VisitorCookie.NAME, issued.toString()));
        MockHttpServletResponse second = new MockHttpServletResponse();
        assertThat(VisitorCookie.ensure(again, second)).isEqualTo(issued);
        assertThat(second.getHeader("Set-Cookie")).isNull();

        MockHttpServletRequest local = new MockHttpServletRequest();
        local.setServerName("localhost");
        MockHttpServletResponse localResponse = new MockHttpServletResponse();
        VisitorCookie.ensure(local, localResponse);
        assertThat(localResponse.getHeader("Set-Cookie")).contains("SameSite=Lax").doesNotContain("Secure");
    }

    @Test
    void visitorKeyPrefersSessionHeaderThenCookieThenIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("9.9.9.9");
        assertThat(VisitorCookie.visitorKey(request, null)).isEqualTo("ip:9.9.9.9");
        UUID id = UUID.randomUUID();
        request.setCookies(new Cookie(VisitorCookie.NAME, id.toString()));
        assertThat(VisitorCookie.visitorKey(request, null)).isEqualTo("a:" + id);
        assertThat(VisitorCookie.visitorKey(request, "sess")).isEqualTo("s:sess");
        request.setCookies(new Cookie(VisitorCookie.NAME, "no-es-uuid"));
        assertThat(VisitorCookie.visitorKey(request, null)).isEqualTo("ip:9.9.9.9");
    }
}
