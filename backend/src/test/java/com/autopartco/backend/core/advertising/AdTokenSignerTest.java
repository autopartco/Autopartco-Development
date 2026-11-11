package com.autopartco.backend.core.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class AdTokenSignerTest {
    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");
    private static final UUID CAMPAIGN = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final AdTokenSigner signer = new AdTokenSigner(AdTokenSigner.deriveKey("secret"), clock);

    @Test
    void signedTokenVerifiesWithItsClaims() {
        String token = signer.sign(CAMPAIGN, PRODUCT, AdPlacement.SEARCH_TOP);

        AdTokenSigner.Claims claims = signer.verify(token).orElseThrow();
        assertThat(claims.campaignId()).isEqualTo(CAMPAIGN);
        assertThat(claims.productId()).isEqualTo(PRODUCT);
        assertThat(claims.placement()).isEqualTo(AdPlacement.SEARCH_TOP);
        assertThat(claims.day()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(claims.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    void alteredPayloadIsIgnored() {
        String token = signer.sign(CAMPAIGN, PRODUCT, AdPlacement.SEARCH_TOP);
        String signature = token.substring(token.indexOf('.') + 1);
        String forgedPayload = CAMPAIGN + "|" + UUID.randomUUID() + "|SEARCH_TOP|2026-11-11|"
                + NOW.plusSeconds(1800).getEpochSecond();
        String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(forgedPayload.getBytes(StandardCharsets.UTF_8)) + "." + signature;

        assertThat(signer.verify(forged)).isEmpty();
    }

    @Test
    void alteredSignatureIsIgnored() {
        String token = signer.sign(CAMPAIGN, PRODUCT, AdPlacement.HOME_FEATURED);
        int index = token.indexOf('.') + 5;
        char current = token.charAt(index);
        String altered = token.substring(0, index) + (current == 'A' ? 'B' : 'A') + token.substring(index + 1);

        assertThat(signer.verify(altered)).isEmpty();
    }

    @Test
    void tokenFromAnotherKeyIsIgnored() {
        String token = new AdTokenSigner(AdTokenSigner.deriveKey("other"), clock)
                .sign(CAMPAIGN, PRODUCT, AdPlacement.SEARCH_TOP);

        assertThat(signer.verify(token)).isEmpty();
    }

    @Test
    void expiredTokenIsIgnored() {
        String token = signer.sign(CAMPAIGN, PRODUCT, AdPlacement.SEARCH_TOP);
        AdTokenSigner later = new AdTokenSigner(AdTokenSigner.deriveKey("secret"),
                Clock.fixed(NOW.plus(Duration.ofMinutes(31)), ZoneOffset.UTC));

        assertThat(later.verify(token)).isEmpty();
    }

    @Test
    void tokenWithTooDistantExpiryIsIgnored() {
        String token = signer.sign(CAMPAIGN, PRODUCT, AdPlacement.SEARCH_TOP, LocalDate.of(2026, 11, 11),
                NOW.plus(Duration.ofDays(2)));

        assertThat(signer.verify(token)).isEmpty();
    }

    @Test
    void garbageIsIgnored() {
        assertThat(signer.verify(null)).isEmpty();
        assertThat(signer.verify("")).isEmpty();
        assertThat(signer.verify("abc")).isEmpty();
        assertThat(signer.verify("a.b.c")).isEmpty();
        assertThat(signer.verify("%%%.%%%")).isEmpty();
        assertThat(signer.verify("x".repeat(500))).isEmpty();
    }

    @Test
    void eventsWithAlteredTokenAreNotRecorded() {
        JdbcClient jdbc = mock(JdbcClient.class);
        AdEvents events = new AdEvents(jdbc, signer, new AdVisitors("salt"), clock);
        String token = signer.sign(CAMPAIGN, PRODUCT, AdPlacement.SEARCH_TOP);
        int index = token.indexOf('.') + 3;
        String altered = token.substring(0, index) + (token.charAt(index) == 'x' ? 'y' : 'x')
                + token.substring(index + 1);

        int accepted = events.record(List.of(altered), "CLICK", "anon-1", null);

        assertThat(accepted).isZero();
        verify(jdbc, never()).sql(anyString());
    }

    @Test
    void eventsRejectOrderKindAndMissingVisitor() {
        JdbcClient jdbc = mock(JdbcClient.class);
        AdEvents events = new AdEvents(jdbc, signer, new AdVisitors("salt"), clock);
        String token = signer.sign(CAMPAIGN, PRODUCT, AdPlacement.SEARCH_TOP);

        assertThat(events.record(List.of(token), "ORDER", "anon-1", null)).isZero();
        assertThat(events.record(List.of(token), "BOGUS", "anon-1", null)).isZero();
        assertThat(events.record(List.of(token), "CLICK", " ", null)).isZero();
        verify(jdbc, never()).sql(anyString());
    }
}
