package com.autopartco.backend.core.analytics;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Origen estimado de una visita. Orden de resolución: código {@code ref} de un enlace compartido, después
 * {@code utm_source}, después el dominio del referente; sin nada, {@link #DIRECT}.
 */
public enum TrafficSource {
    WHATSAPP, FACEBOOK, INSTAGRAM, TIKTOK, X, TELEGRAM, CHATGPT, OTHER_AI, GOOGLE, OTHER_SEARCH, COPY_LINK, INTERNAL,
    DIRECT, OTHER;

    /** Códigos cortos de {@code ?ref=} (y del canal de un compartido). */
    static final Map<String, TrafficSource> REF_CODES = Map.of(
            "wa", WHATSAPP, "fb", FACEBOOK, "ig", INSTAGRAM, "tt", TIKTOK, "x", X, "tg", TELEGRAM, "link", COPY_LINK);

    private static final List<Map.Entry<String, TrafficSource>> HOSTS = List.of(
            Map.entry("whatsapp.com", WHATSAPP), Map.entry("wa.me", WHATSAPP),
            Map.entry("facebook.com", FACEBOOK), Map.entry("fb.com", FACEBOOK), Map.entry("fb.me", FACEBOOK),
            Map.entry("instagram.com", INSTAGRAM),
            Map.entry("tiktok.com", TIKTOK),
            Map.entry("t.co", X), Map.entry("x.com", X), Map.entry("twitter.com", X),
            Map.entry("t.me", TELEGRAM), Map.entry("telegram.org", TELEGRAM), Map.entry("telegram.me", TELEGRAM),
            Map.entry("chatgpt.com", CHATGPT), Map.entry("chat.openai.com", CHATGPT), Map.entry("openai.com", CHATGPT),
            Map.entry("perplexity.ai", OTHER_AI), Map.entry("gemini.google.com", OTHER_AI),
            Map.entry("claude.ai", OTHER_AI), Map.entry("copilot.microsoft.com", OTHER_AI),
            Map.entry("bing.com", OTHER_SEARCH), Map.entry("duckduckgo.com", OTHER_SEARCH),
            Map.entry("search.yahoo.com", OTHER_SEARCH), Map.entry("yahoo.com", OTHER_SEARCH));

    /** Código de canal ({@code wa}, {@code fb}…) o {@code null} si no es válido. */
    public static TrafficSource fromRef(String ref) {
        return ref == null ? null : REF_CODES.get(ref.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * @param ownHost dominio propio del marketplace (sin {@code www.}); un referente propio es {@link #INTERNAL}
     */
    public static TrafficSource resolve(String ref, String utmSource, String referrer, String ownHost) {
        TrafficSource fromRef = fromRef(ref);
        if (fromRef != null) {
            return fromRef;
        }
        if (utmSource != null && !utmSource.isBlank()) {
            String utm = utmSource.trim().toLowerCase(Locale.ROOT);
            TrafficSource byUtm = fromHost(utm.startsWith("www.") ? utm.substring(4) : utm, ownHost);
            if (byUtm != OTHER) {
                return byUtm;
            }
            for (String ai : List.of("perplexity", "gemini", "claude", "copilot")) {
                if (utm.contains(ai)) {
                    return OTHER_AI;
                }
            }
            if (utm.contains("chatgpt") || utm.contains("openai")) {
                return CHATGPT;
            }
        }
        String host = TrafficRecorder.host(referrer);
        return host == null ? DIRECT : fromHost(host, ownHost);
    }

    static TrafficSource fromHost(String host, String ownHost) {
        if (ownHost != null && (host.equals(ownHost) || host.endsWith("." + ownHost))) {
            return INTERNAL;
        }
        for (Map.Entry<String, TrafficSource> entry : HOSTS) {
            String domain = entry.getKey();
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return entry.getValue();
            }
        }
        if (host.equals("google") || host.startsWith("google.") || host.contains(".google.")) {
            return GOOGLE;
        }
        return OTHER;
    }
}
