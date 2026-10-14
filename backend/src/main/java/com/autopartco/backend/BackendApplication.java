package com.autopartco.backend;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		// ADR 0002: LocalDateTime sobre columnas timestamptz se interpreta en la zona de la JVM.
		// El mercado es solo Colombia, así que fijamos America/Bogota antes de arrancar Spring.
		configureTimeZone();
		SpringApplication.run(BackendApplication.class, args);
	}

	static void configureTimeZone() {
		TimeZone.setDefault(TimeZone.getTimeZone("America/Bogota"));
	}

}
