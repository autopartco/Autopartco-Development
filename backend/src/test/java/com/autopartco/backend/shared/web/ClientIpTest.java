package com.autopartco.backend.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpTest {

    @Test
    void usesCloudflareHeaderOverRemoteAddrAndForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        request.addHeader("CF-Connecting-IP", " 190.25.4.7 ");
        assertThat(ClientIp.of(request)).isEqualTo("190.25.4.7");
    }

    @Test
    void acceptsIpv6FromCloudflare() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("CF-Connecting-IP", "2800:e2:1a00::1");
        assertThat(ClientIp.of(request)).isEqualTo("2800:e2:1a00::1");
    }

    @Test
    void fallsBackToRemoteAddrWithoutOrWithInvalidHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        assertThat(ClientIp.of(request)).isEqualTo("10.0.0.5");
        request.addHeader("CF-Connecting-IP", "evil<script>");
        assertThat(ClientIp.of(request)).isEqualTo("10.0.0.5");
    }
}
