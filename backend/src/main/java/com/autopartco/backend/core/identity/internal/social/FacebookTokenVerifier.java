package com.autopartco.backend.core.identity.internal.social;

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Access token de Facebook Login: {@code debug_token} con el token de la app confirma que es válido y que se
 * emitió para {@code FACEBOOK_APP_ID}; luego {@code /me} da el perfil. El correo nunca se da por verificado.
 */
@Component
public class FacebookTokenVerifier implements SocialTokenVerifier {
    static final String GRAPH = "https://graph.facebook.com/v19.0";

    private final String appId;
    private final String appSecret;
    private final Supplier<RestClient> clientSupplier;
    private volatile RestClient client;

    @Autowired
    public FacebookTokenVerifier(SocialAuthProperties properties) {
        this(properties, () -> RestClient.builder().baseUrl(GRAPH).build());
    }

    /** Para pruebas: cliente enlazado a un servidor simulado. */
    FacebookTokenVerifier(SocialAuthProperties properties, Supplier<RestClient> clientSupplier) {
        this.appId = properties.facebook().appId();
        this.appSecret = properties.facebook().appSecret();
        this.clientSupplier = clientSupplier;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.FACEBOOK;
    }

    @Override
    public boolean enabled() {
        return SocialAuthProperties.present(appId) && SocialAuthProperties.present(appSecret);
    }

    @Override
    public SocialIdentity verify(String token) {
        try {
            JsonNode debug = client().get()
                    .uri(uri -> uri.path("/debug_token")
                            .queryParam("input_token", token)
                            .queryParam("access_token", appId + "|" + appSecret)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode data = debug == null ? null : debug.path("data");
            if (data == null || !data.path("is_valid").asBoolean(false) || !appId.equals(data.path("app_id").asText())) {
                throw SocialTokenVerifier.invalidToken();
            }
            String userId = data.path("user_id").asText("");
            JsonNode me = client().get()
                    .uri(uri -> uri.path("/me")
                            .queryParam("fields", "id,first_name,last_name,email")
                            .queryParam("access_token", token)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            if (me == null || userId.isEmpty() || !userId.equals(me.path("id").asText())) {
                throw SocialTokenVerifier.invalidToken();
            }
            return new SocialIdentity(
                    SocialProvider.FACEBOOK,
                    userId,
                    text(me, "email"),
                    false,
                    text(me, "first_name"),
                    text(me, "last_name"));
        } catch (RestClientException exception) {
            throw SocialTokenVerifier.invalidToken();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private RestClient client() {
        RestClient current = client;
        if (current == null) {
            synchronized (this) {
                if (client == null) {
                    client = clientSupplier.get();
                }
                current = client;
            }
        }
        return current;
    }
}
