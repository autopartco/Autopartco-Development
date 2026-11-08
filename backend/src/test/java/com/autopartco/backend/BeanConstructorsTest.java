package com.autopartco.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.util.ClassUtils;

/**
 * Cada bean del escaneo debe tener un constructor que Spring pueda elegir: uno solo, o el marcado con
 * {@code @Autowired}. Si no, el arranque falla con "No default constructor found" (solo se ve al desplegar).
 */
class BeanConstructorsTest {
    @Test
    void everyScannedBeanHasAResolvableConstructor() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(true);
        List<String> broken = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.autopartco.backend")) {
            Class<?> type = ClassUtils.forName(definition.getBeanClassName(), getClass().getClassLoader());
            if (type.isInterface() || type.isAnnotation() || java.lang.reflect.Modifier.isAbstract(type.getModifiers())) {
                continue;
            }
            Constructor<?>[] constructors = type.getDeclaredConstructors();
            if (constructors.length <= 1) {
                continue;
            }
            boolean autowired = Arrays.stream(constructors).anyMatch(constructor -> constructor.isAnnotationPresent(Autowired.class));
            boolean hasDefault = Arrays.stream(constructors).anyMatch(constructor -> constructor.getParameterCount() == 0);
            boolean record = type.isRecord() && BeanUtils.getResolvableConstructor(type) != null;
            if (!autowired && !hasDefault && !record) {
                broken.add(type.getName());
            }
        }
        assertThat(broken).as("beans con varios constructores y ninguno @Autowired").isEmpty();
    }
}
