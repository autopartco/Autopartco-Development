package com.autopartco.backend;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.beans.Introspector;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.stereotype.Component;

import com.autopartco.backend.core.identity.AccountProvisioning;
import com.autopartco.backend.storesuite.staff.AcceptStaffInvitation;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import jakarta.persistence.Entity;

/**
 * Verificación de la estructura modular (Spring Modulith). No levanta el contexto de Spring ni toca la BD.
 *
 * <p>{@code verify()} completo, sin filtros, desde la fase 8.</p>
 */
class ModularityTests {
    private static final String BASE_PACKAGE = "com.autopartco.backend";
    private static final String SPRING_DATA_REPOSITORY = "org.springframework.data.repository.Repository";

    private final ApplicationModules modules = ApplicationModules.of(BackendApplication.class);

    @Test
    void printsModules() {
        System.out.println(modules);
    }

    @Test
    void verifiesModularStructure() {
        // Sin filtros: ciclos, acceso a tipos internos y allowedDependencies de cada package-info (§4.2).
        modules.verify();
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

    @Test
    void onlyAcceptStaffInvitationCreatesInvitedAccounts() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);
        DescribedPredicate<JavaMethodCall> createInvitedAccount = DescribedPredicate.describe(
                "AccountProvisioning#createInvitedAccount",
                call -> call.getTargetOwner().isEquivalentTo(AccountProvisioning.class)
                        && call.getName().equals("createInvitedAccount"));

        // Crear cuentas sin registro propio solo es válido al aceptar una invitación de personal.
        noClasses().that().doNotHaveFullyQualifiedName(AcceptStaffInvitation.class.getName())
                .should().callMethodWhere(createInvitedAccount)
                .check(classes);
        // Evita que la regla anterior pase en vacío si el método o el llamador cambian de nombre.
        classes().that().haveFullyQualifiedName(AcceptStaffInvitation.class.getName())
                .should().callMethodWhere(createInvitedAccount)
                .check(classes);
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
