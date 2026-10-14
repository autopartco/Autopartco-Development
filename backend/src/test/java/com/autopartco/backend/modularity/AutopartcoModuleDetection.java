package com.autopartco.backend.modularity;

import java.util.stream.Stream;

import org.springframework.modulith.core.ApplicationModuleDetectionStrategy;
import org.springframework.modulith.core.JavaPackage;

/**
 * Estrategia de detección de módulos de Autopartco para Spring Modulith.
 *
 * <p>Cada subpaquete directo de {@code com.autopartco.backend} es un módulo, salvo {@code core}: en su lugar,
 * cada {@code core.<dominio>} es un módulo de primer nivel. Así se verifican los límites entre dominios de
 * {@code core} sin anidar módulos ni declarar interfaces nombradas.</p>
 *
 * <p>Solo la usan las pruebas; se registra con la propiedad {@code spring.modulith.detection-strategy}
 * en {@code src/test/resources/config/application.properties}.</p>
 */
public class AutopartcoModuleDetection implements ApplicationModuleDetectionStrategy {

    private static final String CORE_PACKAGE = "core";

    @Override
    public Stream<JavaPackage> getModuleBasePackages(JavaPackage basePackage) {
        String corePackageName = basePackage.getName() + "." + CORE_PACKAGE;

        return basePackage.getDirectSubPackages().stream()
                .flatMap(subPackage -> subPackage.getName().equals(corePackageName)
                        ? subPackage.getDirectSubPackages().stream()
                        : Stream.of(subPackage));
    }
}
