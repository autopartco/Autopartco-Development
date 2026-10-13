package com.autopartco.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import jakarta.validation.Validator;

import com.autopartco.backend.core.identity.web.LoginRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class BackendApplicationTests {

	@Autowired
	private CorsConfigurationSource corsConfigurationSource;

	@Autowired
	private Validator validator;

	@Test
	void contextLoads() {
	}

	@Test
	void allowsFrontendCorsPreflightForAuthentication() {
		MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");
		CorsConfiguration configuration = corsConfigurationSource.getCorsConfiguration(request);

		assertNotNull(configuration);
		assertTrue(configuration.getAllowedOrigins().contains("http://localhost:5173"));
		assertTrue(configuration.getAllowedMethods().contains("POST"));
		assertEquals(Boolean.FALSE, configuration.getAllowCredentials());
	}

	@Test
	void loginAcceptsPasswordsShorterThanRegistrationPolicy() {
		assertTrue(validator.validate(new LoginRequest("user@example.com", "short")).isEmpty());
	}

}
