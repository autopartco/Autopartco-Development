package com.autopartco.backend.core.advertising;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Registro de eventos de anuncios enviados por el navegador ({@code POST /events/ads}). Nunca lanza: un token falso,
 * alterado o vencido, un tipo no permitido ({@code ORDER} lo registra solo el backend) o un visitante desconocido se
 * ignoran. El día es el de Bogotá al recibir el evento. {@code uq_ade_dedupe} + {@code ON CONFLICT DO NOTHING}
 * dejan un evento por día, campaña, producto, ubicación, tipo y visitante: repetir el mismo evento no infla nada.
 */
@Slf4j
@Service
public class AdEvents {
    /** Tope de tokens de impresión por lote. */
    public static final int MAX_BATCH = 24;

    static final String INSERT_SQL = """
            insert into ad_events (day, campaign_id, product_id, placement, kind, visitor_hash)
            select :day, c.id, :productId, :placement, :kind, :visitor
            from ad_campaigns c
            where c.id = :campaignId
            on conflict do nothing
            """;

    private final JdbcClient jdbcClient;
    private final AdTokenSigner signer;
    private final AdVisitors visitors;
    private final Clock clock;

    @Autowired
    public AdEvents(JdbcClient jdbcClient, AdTokenSigner signer, AdVisitors visitors) {
        this(jdbcClient, signer, visitors, Clock.systemUTC());
    }

    AdEvents(JdbcClient jdbcClient, AdTokenSigner signer, AdVisitors visitors, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.signer = signer;
        this.visitors = visitors;
        this.clock = clock;
    }

    /**
     * Registra un evento por cada token válido de {@code tokens} (como mucho {@value #MAX_BATCH}).
     *
     * @param kind        tipo pedido por el cliente (texto); desconocido u {@code ORDER} → se ignora
     * @param anonymousId id anónimo del navegador (si no hay sesión)
     * @param accountId   cuenta con sesión, o nulo
     * @return eventos aceptados (insertados o ya existentes)
     */
    public int record(List<String> tokens, String kind, String anonymousId, UUID accountId) {
        Optional<AdEventKind> parsed = AdEventKind.parse(kind);
        if (tokens == null || tokens.isEmpty() || parsed.isEmpty() || !parsed.get().clientReportable()) {
            return 0;
        }
        String visitor = accountId != null ? visitors.ofAccount(accountId) : visitors.ofAnonymous(anonymousId);
        if (visitor == null) {
            return 0;
        }
        LocalDate day = AdText.today(clock);
        int accepted = 0;
        for (String token : tokens.stream().limit(MAX_BATCH).distinct().toList()) {
            Optional<AdTokenSigner.Claims> claims = signer.verify(token);
            if (claims.isEmpty()) {
                continue;
            }
            try {
                jdbcClient.sql(INSERT_SQL)
                        .param("day", day)
                        .param("campaignId", claims.get().campaignId())
                        .param("productId", claims.get().productId())
                        .param("placement", claims.get().placement().name())
                        .param("kind", parsed.get().name())
                        .param("visitor", visitor)
                        .update();
                accepted++;
            } catch (RuntimeException failure) {
                log.warn("Ad event not recorded: {}", failure.getClass().getSimpleName());
            }
        }
        return accepted;
    }
}
