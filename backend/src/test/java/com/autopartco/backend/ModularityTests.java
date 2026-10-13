package com.autopartco.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.Introspector;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.stereotype.Component;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import jakarta.persistence.Entity;

/**
 * Verificación de la estructura modular (Spring Modulith). No levanta el contexto de Spring ni toca la BD.
 *
 * <p>Crece por fases: la verificación filtrada a los módulos migrados se añade en la fase 1 y
 * {@code verify()} completo en la fase 8.</p>
 */
class ModularityTests {
    private static final Pattern MIGRATED = Pattern.compile("Module '(core\\.audit|core\\.notification)'");
    private static final Pattern LEGACY = Pattern.compile("com\\.autopartco\\.backend\\.(auth|seller|store|product)\\.");

    private static final String BASE_PACKAGE = "com.autopartco.backend";
    private static final String SPRING_DATA_REPOSITORY = "org.springframework.data.repository.Repository";

    private final ApplicationModules modules = ApplicationModules.of(BackendApplication.class);

    @Test
    void printsModules() {
        System.out.println(modules);

        System.out.println("Violaciones detectadas (informativo hasta la fase 8): "
                + modules.detectViolations().getMessages().size());
    }

    @Test
    void migratedModulesHaveNoViolations() {
        // Durante la migración (§7) se permiten dependencias hacia los paquetes legados.
        modules.detectViolations()
                .filter(violation -> MIGRATED.matcher(violation.getMessage()).find()
                        && !LEGACY.matcher(violation.getMessage()).find())
                .throwIfPresent();
    }

    @Test
    void springBeanAndEntityNamesAreUnique() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);

        Map<String, List<String>> beansByName = classes.stream()
                .map(javaClass -> beanName(javaClass).map(name -> Map.entry(name, javaClass.getName())))
                .flatMap(Optional::stream)
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        Map<String, List<String>> entitiesByName = classes.stream()
                .filter(javaClass -> javaClass.isAnnotatedWith(Entity.class))
                .collect(Collectors.groupingBy(ModularityTests::entityName,
                        Collectors.mapping(JavaClass::getName, Collectors.toList())));

        assertThat(duplicates(beansByName)).as("Nombres de bean de Spring duplicados").isEmpty();
        assertThat(duplicates(entitiesByName)).as("Nombres de entidad JPA duplicados").isEmpty();
    }

    private static Optional<String> beanName(JavaClass javaClass) {
        if (javaClass.isInterface() && javaClass.isAssignableTo(SPRING_DATA_REPOSITORY)) {
            return Optional.of(Introspector.decapitalize(javaClass.getSimpleName()));
        }
        if (javaClass.isInterface() || javaClass.isAnnotation()
                || !javaClass.isMetaAnnotatedWith(Component.class)) {
            return Optional.empty();
        }
        String explicitName = javaClass.getAnnotations().stream()
                .filter(ModularityTests::isComponentAnnotation)
                .map(annotation -> annotation.get("value").orElse(""))
                // @ControllerAdvice usa "value" como String[] (paquetes base), no como nombre de bean.
                .filter(value -> value instanceof String name && !name.isBlank())
                .map(String.class::cast)
                .findFirst()
                .orElse(null);
        return Optional.of(explicitName != null ? explicitName : Introspector.decapitalize(javaClass.getSimpleName()));
    }

    private static boolean isComponentAnnotation(JavaAnnotation<?> annotation) {
        JavaClass type = annotation.getRawType();
        return type.isEquivalentTo(Component.class) || type.isMetaAnnotatedWith(Component.class);
    }

    private static String entityName(JavaClass javaClass) {
        String explicitName = javaClass.getAnnotationOfType(Entity.class).name();
        return explicitName.isBlank() ? javaClass.getSimpleName() : explicitName;
    }

    private static Map<String, List<String>> duplicates(Map<String, List<String>> classesByName) {
        return classesByName.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
