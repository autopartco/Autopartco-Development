package com.autopartco.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import jakarta.validation.Validator;

import com.autopartco.backend.core.identity.web.LoginRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
		// Garantia explicita (precedencia maxima) de que .env no redirige la BD ni habilita DDL.
		"spring.datasource.url=jdbc:postgresql://127.0.0.1:1/autopartco_test_isolated",
		"spring.datasource.username=test",
		"spring.datasource.password=test",
		"spring.jpa.hibernate.ddl-auto=none"
})
@ActiveProfiles("test")
class BackendApplicationTests {

	@Autowired
	private Environment environment;

	@Autowired
	private CorsConfigurationSource corsConfigurationSource;

	@Test
	void usesIsolatedDatabaseAndNeverRunsDdl() {
		assertEquals("none", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
		assertTrue(environment.getProperty("spring.datasource.url").contains("autopartco_test_isolated"));
	}

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
