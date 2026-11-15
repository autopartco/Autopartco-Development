package com.autopartco.backend.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpTest {

    private static final String CLOUDFLARE_EDGE = "172.70.1.2";

    @AfterEach
    void restoreDefaultRanges() {
        ClientIp.trustProxies(ClientIp.CLOUDFLARE_RANGES);
    }

    @Test
    void ignoresSpoofedCloudflareHeaderFromNonCloudflareAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("CF-Connecting-IP", "190.25.4.7");
        assertThat(ClientIp.of(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void ignoresCloudflareHeaderFromLocalProxyByDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("CF-Connecting-IP", "190.25.4.7");
        assertThat(ClientIp.of(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void usesCloudflareHeaderFromCloudflareAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(CLOUDFLARE_EDGE);
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        request.addHeader("CF-Connecting-IP", " 190.25.4.7 ");
        assertThat(ClientIp.of(request)).isEqualTo("190.25.4.7");
    }

    @Test
    void acceptsIpv6FromCloudflareIpv6Edge() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("2606:4700:10::ac43:1");
        request.addHeader("CF-Connecting-IP", "2800:e2:1a00::1");
        assertThat(ClientIp.of(request)).isEqualTo("2800:e2:1a00::1");
    }

    @Test
    void configuredRangesReplaceDefaults() {
        ClientIp.trustProxies(List.of("127.0.0.1/32"));
        MockHttpServletRequest local = new MockHttpServletRequest();
        local.setRemoteAddr("127.0.0.1");
        local.addHeader("CF-Connecting-IP", "190.25.4.7");
        assertThat(ClientIp.of(local)).isEqualTo("190.25.4.7");

        MockHttpServletRequest edge = new MockHttpServletRequest();
        edge.setRemoteAddr(CLOUDFLARE_EDGE);
        edge.addHeader("CF-Connecting-IP", "190.25.4.7");
        assertThat(ClientIp.of(edge)).isEqualTo(CLOUDFLARE_EDGE);
    }

    @Test
    void emptyRangesNeverTrustHeader() {
        ClientIp.trustProxies(List.of());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(CLOUDFLARE_EDGE);
        request.addHeader("CF-Connecting-IP", "190.25.4.7");
        assertThat(ClientIp.of(request)).isEqualTo(CLOUDFLARE_EDGE);
    }

    @Test
    void hexLookingRemoteAddrIsNotResolvedOrTrusted() {
        assertThat(ClientIp.isTrusted("dead")).isFalse();
    }

    @Test
    void fallsBackToRemoteAddrWithInvalidHeaderFromCloudflare() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(CLOUDFLARE_EDGE);
        request.addHeader("CF-Connecting-IP", "evil<script>");
        assertThat(ClientIp.of(request)).isEqualTo(CLOUDFLARE_EDGE);
    }

    @Test
    void stripsBracketsAndPortFromIpv6RemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("[2800:e2:1680:1e6:707d:971e:111b:fbf6]");
        assertThat(ClientIp.of(request)).isEqualTo("2800:e2:1680:1e6:707d:971e:111b:fbf6");
        request.setRemoteAddr("[::1]:8081");
        assertThat(ClientIp.of(request)).isEqualTo("::1");
    }

    @Test
    void stripsBracketsFromCloudflareHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(CLOUDFLARE_EDGE);
        request.addHeader("CF-Connecting-IP", "[2800:e2:1a00::1]");
        assertThat(ClientIp.of(request)).isEqualTo("2800:e2:1a00::1");
    }

    @Test
    void usesPlaceholderWhenNoValidIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("unknown");
        assertThat(ClientIp.of(request)).isEqualTo(ClientIp.UNKNOWN);
    }
}
