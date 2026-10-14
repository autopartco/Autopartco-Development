package com.autopartco.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifica que la zona por defecto de la JVM se fija a America/Bogota (ADR 0002).
 * Prueba unitaria sin Spring: no arranca el contexto ni toca la BD.
 */
class BackendTimeZoneTest {

	private TimeZone originalDefault;

	@BeforeEach
	void saveDefault() {
		originalDefault = TimeZone.getDefault();
	}

	@AfterEach
	void restoreDefault() {
		TimeZone.setDefault(originalDefault);
	}

	@Test
	void configureTimeZoneSetsBogotaAsJvmDefault() {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

		BackendApplication.configureTimeZone();

		assertThat(TimeZone.getDefault().getID()).isEqualTo("America/Bogota");
	}
}
