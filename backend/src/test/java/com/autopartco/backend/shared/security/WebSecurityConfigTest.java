package com.autopartco.backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.Filter;

/**
 * Prueba de la cadena de seguridad HTTP real ({@link WebSecurityConfig}) sin JPA ni base de datos.
 *
 * <p>Levanta un contexto mínimo de Spring MVC con la configuración de seguridad, CORS, los manejadores 401/403,
 * claves RSA generadas en memoria (el {@code JwtDecoder} real valida los tokens) y un controlador comodín que
 * responde 200 a cualquier ruta. Así se comprueba solo la autorización por ruta.</p>
 */
@SpringJUnitWebConfig(WebSecurityConfigTest.TestConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class WebSecurityConfigTest {

    private static final String BASE = "/api/v1";
    private static final String ID = "3f0c9a52-6f43-4a5e-9a51-2a1f7c0d9b10";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtEncoder jwtEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Filter securityFilterChain = context.getBean("springSecurityFilterChain", Filter.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
    }

    static Stream<Arguments> publicRoutes() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, BASE + "/auth/register"),
                Arguments.of(HttpMethod.POST, BASE + "/auth/login"),
                Arguments.of(HttpMethod.POST, BASE + "/auth/refresh"),
                Arguments.of(HttpMethod.GET, BASE + "/auth/verify-email"),
                Arguments.of(HttpMethod.POST, BASE + "/auth/unlock/request"),
                Arguments.of(HttpMethod.GET, BASE + "/auth/unlock"),
                Arguments.of(HttpMethod.POST, BASE + "/auth/password-recovery/request"),
                Arguments.of(HttpMethod.POST, BASE + "/auth/password-recovery/reset"),
                Arguments.of(HttpMethod.POST, BASE + "/webhooks/wompi"),
                Arguments.of(HttpMethod.POST, BASE + "/store-applications"),
                Arguments.of(HttpMethod.GET, BASE + "/staff-invitations/preview"),
                Arguments.of(HttpMethod.POST, BASE + "/staff-invitations/accept"),
                Arguments.of(HttpMethod.GET, BASE + "/plans"),
                Arguments.of(HttpMethod.GET, BASE + "/marketplace/stores"),
                Arguments.of(HttpMethod.GET, BASE + "/marketplace/stores/" + ID + "/brand/logo"),
                Arguments.of(HttpMethod.GET, BASE + "/marketplace/products"),
                Arguments.of(HttpMethod.GET, BASE + "/marketplace/products/" + ID),
                Arguments.of(HttpMethod.GET, BASE + "/marketplace/product-options"),
                Arguments.of(HttpMethod.GET, BASE + "/marketplace/product-options/manufacturers"),
                Arguments.of(HttpMethod.GET, BASE + "/marketplace/product-options/categories"),
                Arguments.of(HttpMethod.GET, BASE + "/marketplace/product-options/compatibilities"),
                Arguments.of(HttpMethod.GET, "/actuator/health"));
    }

    static Stream<Arguments> protectedRoutes() {
        return Stream.of(
                // Sesión e identidad
                Arguments.of(HttpMethod.POST, BASE + "/auth/logout"),
                Arguments.of(HttpMethod.POST, BASE + "/auth/logout/others"),
                Arguments.of(HttpMethod.GET, BASE + "/me/profile"),
                Arguments.of(HttpMethod.GET, BASE + "/me/sessions"),
                Arguments.of(HttpMethod.GET, BASE + "/me/notifications"),
                Arguments.of(HttpMethod.POST, BASE + "/me/store-applications"),
                Arguments.of(HttpMethod.POST, BASE + "/me/store-applications/" + ID + "/setup"),
                Arguments.of(HttpMethod.POST, BASE + "/me/store-applications/" + ID + "/setup-link"),
                Arguments.of(HttpMethod.GET, BASE + "/me/store-applications/" + ID + "/documents/" + ID),
                // Store Suite
                Arguments.of(HttpMethod.PATCH, BASE + "/stores/" + ID),
                Arguments.of(HttpMethod.GET, BASE + "/stores/" + ID + "/status"),
                Arguments.of(HttpMethod.GET, BASE + "/stores/" + ID + "/products"),
                Arguments.of(HttpMethod.POST, BASE + "/stores/" + ID + "/branches"),
                Arguments.of(HttpMethod.GET, BASE + "/stores/" + ID + "/orders"),
                Arguments.of(HttpMethod.GET, BASE + "/stores/" + ID + "/staff"),
                Arguments.of(HttpMethod.POST, BASE + "/stores/" + ID + "/subscription/checkout"),
                Arguments.of(HttpMethod.PUT, BASE + "/stores/" + ID + "/promotions/" + ID),
                // Administración (anónimo: 401 antes que 403)
                Arguments.of(HttpMethod.GET, BASE + "/admin/categories"),
                Arguments.of(HttpMethod.GET, BASE + "/admin/store-applications/pending"),
                // Rutas públicas con otro método o más profundidad no quedan abiertas
                Arguments.of(HttpMethod.POST, BASE + "/marketplace/products"),
                Arguments.of(HttpMethod.GET, BASE + "/marketplace/products/" + ID + "/extra"),
                Arguments.of(HttpMethod.DELETE, BASE + "/plans"),
                Arguments.of(HttpMethod.GET, BASE + "/store-applications"),
                Arguments.of(HttpMethod.POST, BASE + "/store-applications/setup"),
                Arguments.of(HttpMethod.POST, BASE + "/store-applications/setup/resend"),
                Arguments.of(HttpMethod.GET, BASE + "/staff-invitations/accept"),
                // Rutas eliminadas o antiguas: caen en "autenticado"
                Arguments.of(HttpMethod.GET, BASE + "/test/public"),
                Arguments.of(HttpMethod.GET, BASE + "/products/published"),
                Arguments.of(HttpMethod.GET, BASE + "/seller-plans"),
                Arguments.of(HttpMethod.GET, BASE + "/seller-applications/stores/" + ID + "/brand/logo"));
    }

    static Stream<Arguments> adminRoutes() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, BASE + "/admin/categories"),
                Arguments.of(HttpMethod.DELETE, BASE + "/admin/manufacturers/" + ID),
                Arguments.of(HttpMethod.POST, BASE + "/admin/plans"),
                Arguments.of(HttpMethod.PUT, BASE + "/admin/promotions/" + ID),
                Arguments.of(HttpMethod.GET, BASE + "/admin/store-applications/pending"),
                Arguments.of(HttpMethod.PATCH, BASE + "/admin/store-applications/" + ID + "/review"),
                Arguments.of(HttpMethod.GET, BASE + "/admin/store-applications/" + ID + "/documents/" + ID));
    }

    @Test
    void publicRouteListMatchesTheRoutesThatWerePublicBeforeTheRefactor() {
        // Literales a propósito: cualquier cambio en la superficie pública debe tocar también esta lista.
        assertThat(WebSecurityConfig.PUBLIC_ROUTES)
                .extracting(route -> route.method().name() + " " + route.pattern())
                .containsExactlyInAnyOrder(
                        "POST /api/v1/auth/register",
                        "POST /api/v1/auth/login",
                        "POST /api/v1/auth/refresh",
                        "GET /api/v1/auth/verify-email",
                        "POST /api/v1/auth/unlock/request",
                        "GET /api/v1/auth/unlock",
                        "POST /api/v1/auth/password-recovery/request",
                        "POST /api/v1/auth/password-recovery/reset",
                        "POST /api/v1/webhooks/wompi",
                        "POST /api/v1/store-applications",
                        "GET /api/v1/staff-invitations/preview",
                        "POST /api/v1/staff-invitations/accept",
                        "GET /api/v1/plans",
                        "GET /api/v1/marketplace/stores",
                        "GET /api/v1/marketplace/stores/*/brand/*",
                        "GET /api/v1/marketplace/products",
                        "GET /api/v1/marketplace/products/*",
                        "GET /api/v1/marketplace/product-options",
                        "GET /api/v1/marketplace/product-options/*");
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("publicRoutes")
    void publicRoutesAreReachableAnonymously(HttpMethod method, String path) throws Exception {
        mockMvc.perform(request(method, path)).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("protectedRoutes")
    void protectedRoutesRejectAnonymousRequests(HttpMethod method, String path) throws Exception {
        mockMvc.perform(request(method, path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("protectedRoutes")
    void protectedNonAdminRoutesAcceptAuthenticatedRequests(HttpMethod method, String path) throws Exception {
        if (path.startsWith(BASE + "/admin/")) {
            return;
        }
        mockMvc.perform(withToken(request(method, path), "ROLE_CUSTOMER")).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("adminRoutes")
    void adminRoutesAreForbiddenForAuthenticatedNonRootUsers(HttpMethod method, String path) throws Exception {
        mockMvc.perform(withToken(request(method, path), "ROLE_CUSTOMER", "ROLE_STORE_MANAGER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("adminRoutes")
    void adminRoutesAreAllowedForRoot(HttpMethod method, String path) throws Exception {
        mockMvc.perform(withToken(request(method, path), "ROLE_ROOT")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-jwt", "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ4In0."})
    void invalidTokensAreRejected(String token) throws Exception {
        mockMvc.perform(request(HttpMethod.GET, BASE + "/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder builder, String... roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("user@example.com")
                .issuer("autopartco")
                .issuedAt(now)
                .expiresAt(now.plus(5, ChronoUnit.MINUTES))
                .claim("roles", List.of(roles))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    @Configuration
    @EnableWebMvc
    @Import({WebSecurityConfig.class, CorsConfig.class, UnauthorizedEntryPoint.class, ForbiddenHandler.class,
            CatchAllController.class})
    static class TestConfig {

        @Bean
        KeyPair testKeyPair() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        }

        @Bean
        RSAPublicKey rsaPublicKey(KeyPair testKeyPair) {
            return (RSAPublicKey) testKeyPair.getPublic();
        }

        @Bean
        RSAPrivateKey rsaPrivateKey(KeyPair testKeyPair) {
            return (RSAPrivateKey) testKeyPair.getPrivate();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    /** Responde 200 a cualquier ruta: la prueba solo mide la decisión de la cadena de seguridad. */
    @RestController
    static class CatchAllController {

        @RequestMapping("/**")
        ResponseEntity<Void> any() {
            return ResponseEntity.ok().build();
        }
    }
}
