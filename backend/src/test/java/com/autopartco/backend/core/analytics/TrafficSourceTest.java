package com.autopartco.backend.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TrafficSourceTest {
    private static final String OWN = "autopartco.co";

    @ParameterizedTest(name = "ref={0} utm={1} referrer={2} → {3}")
    @CsvSource(nullValues = "-", value = {
            // ref del enlace compartido gana sobre todo lo demás
            "wa,   -,           https://www.google.com/,             WHATSAPP",
            "WA,   -,           -,                                   WHATSAPP",
            "tt,   chatgpt.com, -,                                   TIKTOK",
            "link, -,           -,                                   COPY_LINK",
            // ref desconocido se ignora
            "zz,   -,           -,                                   DIRECT",
            // utm_source (ChatGPT añade utm_source=chatgpt.com)
            "-,    chatgpt.com, -,                                   CHATGPT",
            "-,    perplexity,  -,                                   OTHER_AI",
            "-,    gemini.google.com, -,                             OTHER_AI",
            "-,    newsletter,  https://l.facebook.com/l.php,        FACEBOOK",
            // referente
            "-,    -,           https://l.facebook.com/l.php?u=x,    FACEBOOK",
            "-,    -,           https://m.facebook.com/,             FACEBOOK",
            "-,    -,           https://l.instagram.com/,            INSTAGRAM",
            "-,    -,           https://www.tiktok.com/@a,           TIKTOK",
            "-,    -,           https://t.co/abc,                    X",
            "-,    -,           https://web.whatsapp.com/,           WHATSAPP",
            "-,    -,           https://t.me/canal,                  TELEGRAM",
            "-,    -,           https://chatgpt.com/,                CHATGPT",
            "-,    -,           https://claude.ai/chat,              OTHER_AI",
            "-,    -,           https://www.google.com.co/search?q=, GOOGLE",
            "-,    -,           https://www.google.com/,             GOOGLE",
            "-,    -,           https://www.bing.com/,               OTHER_SEARCH",
            "-,    -,           https://www.autopartco.co/tiendas,   INTERNAL",
            "-,    -,           https://foro.ejemplo.com/,           OTHER",
            "-,    -,           no es una url,                       DIRECT",
            "-,    -,           -,                                   DIRECT",
    })
    void resolvesSource(String ref, String utm, String referrer, TrafficSource expected) {
        assertThat(TrafficSource.resolve(ref, utm, referrer, OWN)).isEqualTo(expected);
    }
}
