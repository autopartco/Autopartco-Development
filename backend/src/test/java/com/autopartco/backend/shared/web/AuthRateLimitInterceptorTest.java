package com.autopartco.backend.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.shared.error.GlobalExceptionHandler;

class AuthRateLimitInterceptorTest {
    @RestController
    static class FakeAuth {
        @PostMapping("/api/v1/auth/login")
        String login() {
            return "ok";
        }

        @PostMapping("/api/v1/auth/refresh")
        String refresh() {
            return "ok";
        }

        @GetMapping("/api/v1/auth/verify-email")
        String verify() {
            return "ok";
        }
    }

    private MockMvc mvc(int perMinute) {
        AuthRateLimitInterceptor interceptor =
                new AuthRateLimitInterceptor(RateLimiters.inMemory(Clock.systemUTC()), perMinute);
        return MockMvcBuilders.standaloneSetup(new FakeAuth())
                .addInterceptors(interceptor)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void limitsLoginPerIpAndRoute() throws Exception {
        MockMvc mvc = mvc(10);
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/v1/auth/login")).andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/auth/login"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("AUTH_RATE_LIMITED"));
        // Otra IP y otra ruta limitada tienen su propio contador; refresh no se limita.
        mvc.perform(post("/api/v1/auth/login").with(request -> {
            request.setRemoteAddr("10.0.0.2");
            return request;
        })).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/verify-email")).andExpect(status().isOk());
        for (int i = 0; i < 15; i++) {
            mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isOk());
        }
    }

    @Test
    void routeGroups() {
        assertThat(AuthRateLimitInterceptor.group("POST", "/api/v1/auth/login/two-factor/resend"))
                .isEqualTo("login-2fa-resend");
        assertThat(AuthRateLimitInterceptor.group("POST", "/api/v1/auth/verify-email/resend"))
                .isEqualTo("verify-resend");
        assertThat(AuthRateLimitInterceptor.group("POST", "/api/v1/auth/social/google")).isEqualTo("social");
        assertThat(AuthRateLimitInterceptor.group("POST", "/api/v1/auth/register")).isNull();
        assertThat(AuthRateLimitInterceptor.group("POST", "/api/v1/auth/logout")).isNull();
    }
}
