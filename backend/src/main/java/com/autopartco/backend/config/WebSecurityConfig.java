package com.autopartco.backend.config;

import com.autopartco.backend.exception.ForbiddenHandler;
import com.autopartco.backend.exception.UnauthorizedEntryPoint;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({AppProperties.class, WompiProperties.class})
public class WebSecurityConfig {

        public final static String LOGIN_URL_MATCHER = ApiConfig.API_BASE_PATH + "/auth/login";
        public final static String REFRESH_URL_MATCHER = ApiConfig.API_BASE_PATH + "/auth/refresh";
        public final static String REGISTER_URL_MATCHER = ApiConfig.API_BASE_PATH + "/auth/register";
        public final static String SELLER_REGISTER_URL_MATCHER = ApiConfig.API_BASE_PATH + "/auth/register/seller";
        public final static String VERIFY_EMAIL_URL_MATCHER = ApiConfig.API_BASE_PATH + "/auth/verify-email";
        public final static String REQUEST_UNLOCK_URL_MATCHER = ApiConfig.API_BASE_PATH + "/auth/unlock/request";
        public final static String UNLOCK_URL_MATCHER = ApiConfig.API_BASE_PATH + "/auth/unlock";
        public final static String PASSWORD_RECOVERY_REQUEST_URL_MATCHER =
                ApiConfig.API_BASE_PATH + "/auth/password-recovery/request";
        public final static String PASSWORD_RECOVERY_RESET_URL_MATCHER =
                ApiConfig.API_BASE_PATH + "/auth/password-recovery/reset";
        public final static String LOG_OUT_MATCHER = ApiConfig.API_BASE_PATH + "/auth/logout";
        public final static String SELLER_SETUP_MATCHER = ApiConfig.API_BASE_PATH + "/seller-applications/setup";
        public final static String SELLER_PLANS_MATCHER = ApiConfig.API_BASE_PATH + "/seller-plans";
        public final static String SELLER_BRAND_MATCHER = ApiConfig.API_BASE_PATH + "/seller-applications/stores/*/brand/*";
        public final static String WOMPI_WEBHOOK_MATCHER = ApiConfig.API_BASE_PATH + "/seller-payments/wompi/webhook";
        public final static String PUBLISHED_STORES_MATCHER = ApiConfig.API_BASE_PATH + "/stores/published";
        public final static String PUBLISHED_PRODUCTS_MATCHER = ApiConfig.API_BASE_PATH + "/products/published";
        public final static String PRODUCT_DETAIL_MATCHER = ApiConfig.API_BASE_PATH + "/products/*";
        public final static String PRODUCT_OPTIONS_MATCHER = ApiConfig.API_BASE_PATH + "/products/options";
        final String BASE_URL_MATCHER = ApiConfig.API_BASE_PATH + "/**";

        final String[] PUBLIC_ENDPOINTS = {
                "/actuator/health",
                "/api/v1/test/public"
        };

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity, UnauthorizedEntryPoint unauthorizedEntryPoint, ForbiddenHandler forbiddenHandler) throws Exception {
                
                return httpSecurity
                        .csrf(csrf -> csrf.disable())
                        .cors(Customizer.withDefaults())

                        .sessionManagement(session ->
                                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                        )

                        .exceptionHandling(exception -> exception
                                .authenticationEntryPoint(unauthorizedEntryPoint)
                                .accessDeniedHandler(forbiddenHandler)
                        )

                        .authorizeHttpRequests(auth -> auth
                                .requestMatchers(HttpMethod.POST, LOGIN_URL_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.POST, REFRESH_URL_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.POST, REGISTER_URL_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.POST, SELLER_REGISTER_URL_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.GET, VERIFY_EMAIL_URL_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.POST, REQUEST_UNLOCK_URL_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.GET, UNLOCK_URL_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.POST, PASSWORD_RECOVERY_REQUEST_URL_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.POST, PASSWORD_RECOVERY_RESET_URL_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.POST, SELLER_SETUP_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.POST, WOMPI_WEBHOOK_MATCHER).permitAll()
                                .requestMatchers(
                                        HttpMethod.GET,
                                        ApiConfig.API_BASE_PATH + "/seller-dashboard/invitations/preview")
                                        .permitAll()
                                .requestMatchers(
                                        HttpMethod.POST,
                                        ApiConfig.API_BASE_PATH + "/seller-dashboard/invitations/accept")
                                        .permitAll()
                                .requestMatchers(HttpMethod.GET, SELLER_PLANS_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.GET, SELLER_BRAND_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.POST, LOG_OUT_MATCHER).authenticated()
                                .requestMatchers(HttpMethod.GET, ApiConfig.API_BASE_PATH + "/stores/*/status").authenticated()
                                .requestMatchers(HttpMethod.PATCH, ApiConfig.API_BASE_PATH + "/stores/*").authenticated()
                                .requestMatchers(HttpMethod.GET, PUBLISHED_STORES_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.GET, PUBLISHED_PRODUCTS_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.GET, PRODUCT_DETAIL_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.GET, PRODUCT_OPTIONS_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.GET, ApiConfig.API_BASE_PATH + "/products/options/*")
                                        .permitAll()
                                .requestMatchers(ApiConfig.API_BASE_PATH + "/categories/**").hasAuthority("ROLE_ROOT")
                                .requestMatchers(ApiConfig.API_BASE_PATH + "/manufacturers/**").hasAuthority("ROLE_ROOT")
                                .requestMatchers(
                                        ApiConfig.API_BASE_PATH + "/platform/promotions",
                                        ApiConfig.API_BASE_PATH + "/platform/promotions/**")
                                        .hasAuthority("ROLE_ROOT")
                                .requestMatchers(
                                        ApiConfig.API_BASE_PATH + "/stores/*/promotions",
                                        ApiConfig.API_BASE_PATH + "/stores/*/promotions/**")
                                        .authenticated()
                                .requestMatchers(HttpMethod.GET, ApiConfig.API_BASE_PATH + "/stores/*/branches")
                                        .authenticated()
                                .requestMatchers(HttpMethod.POST, ApiConfig.API_BASE_PATH + "/stores/*/branches")
                                        .authenticated()
                                .requestMatchers(HttpMethod.PATCH, ApiConfig.API_BASE_PATH + "/stores/*/branches/*")
                                        .authenticated()
                                .requestMatchers(HttpMethod.DELETE, ApiConfig.API_BASE_PATH + "/stores/*/branches/*")
                                        .authenticated()
                                .requestMatchers(HttpMethod.GET, ApiConfig.API_BASE_PATH + "/stores/*/products")
                                        .authenticated()
                                .requestMatchers(HttpMethod.POST, ApiConfig.API_BASE_PATH + "/stores/*/products")
                                        .authenticated()
                                .requestMatchers(HttpMethod.PATCH, ApiConfig.API_BASE_PATH + "/stores/*/branches/*/products/*")
                                        .authenticated()
                                .requestMatchers(HttpMethod.PATCH, ApiConfig.API_BASE_PATH + "/stores/*/products/*/publication")
                                        .authenticated()
                                .requestMatchers(HttpMethod.POST, ApiConfig.API_BASE_PATH + "/stores/*/products/*/images")
                                        .authenticated()
                                .requestMatchers(HttpMethod.PUT, ApiConfig.API_BASE_PATH + "/stores/*/products/*/images/*")
                                        .authenticated()
                                .requestMatchers(HttpMethod.PATCH, ApiConfig.API_BASE_PATH + "/stores/*/products/*/images/*")
                                        .authenticated()
                                .requestMatchers(HttpMethod.DELETE, ApiConfig.API_BASE_PATH + "/stores/*/products/*/images/*")
                                        .authenticated()
                                .requestMatchers(HttpMethod.DELETE, ApiConfig.API_BASE_PATH + "/stores/*/products/*")
                                        .authenticated()
                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                                .anyRequest().authenticated()
                        )

                        //  Configuracion de recursos para usar JWT
                        .oauth2ResourceServer(oauth2 -> oauth2
                                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        )

                        .formLogin(form -> form.disable())
                        
                        .httpBasic(basic -> basic.disable())

                        .build();
        }

        @Bean
        public JwtAuthenticationConverter jwtAuthenticationConverter() {
                JwtGrantedAuthoritiesConverter authoritiesConverter =
                        new JwtGrantedAuthoritiesConverter();
                authoritiesConverter.setAuthoritiesClaimName("roles");
                authoritiesConverter.setAuthorityPrefix("");

                JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
                converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
                return converter;
        }

        @Bean
        public JwtEncoder jwtEncoder(RSAPublicKey pub, RSAPrivateKey pri) {
                JWK jwk = new RSAKey.Builder(pub).privateKey(pri).build();
                JWKSource<SecurityContext> jwks = new ImmutableJWKSet<>(new JWKSet(jwk));
                return new NimbusJwtEncoder(jwks);
        }

        @Bean
        public JwtDecoder jwtDecoder(RSAPublicKey pub) {
                return NimbusJwtDecoder.withPublicKey(pub).build();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }
}
