package com.autopartco.backend.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.operations.staff.StaffAdminController;
import com.autopartco.backend.operations.support.AccountsAdmin;
import com.autopartco.backend.shared.error.ForbiddenException;
import com.autopartco.backend.shared.security.PlatformAccess;
import com.autopartco.backend.shared.web.ApiPaths;

/** Cada endpoint de {@code /admin} exige un permiso de {@link PlatformAccess}; el equipo solo lo gestiona ROOT. */
class AdminAccessTest {

    @Test
    void everyAdminHandlerRequiresAPlatformAccessExpression() throws Exception {
        Set<String> allowed = new HashSet<>();
        for (Field field : PlatformAccess.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                allowed.add((String) field.get(null));
            }
        }
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        int checked = 0;
        for (var candidate : scanner.findCandidateComponents("com.autopartco.backend")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            RequestMapping mapping = type.getAnnotation(RequestMapping.class);
            if (mapping == null || mapping.value().length == 0 || !mapping.value()[0].startsWith(ApiPaths.ADMIN)) {
                continue;
            }
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) {
                    continue;
                }
                PreAuthorize pre = method.getAnnotation(PreAuthorize.class);
                assertThat(pre).as(type.getSimpleName() + "." + method.getName()).isNotNull();
                assertThat(allowed).as(type.getSimpleName() + "." + method.getName()).contains(pre.value());
                if (type == StaffAdminController.class) {
                    assertThat(pre.value()).as("equipo solo ROOT: " + method.getName()).isEqualTo(PlatformAccess.ROOT);
                }
                checked++;
            }
        }
        assertThat(checked).isGreaterThan(50);
    }

    @Test
    void platformAccessAlwaysIncludesRoot() throws Exception {
        for (Field field : PlatformAccess.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                assertThat((String) field.get(null)).as(field.getName()).contains("'ROLE_ROOT'");
            }
        }
    }

    @Test
    void accountActionsProtectRootSelfAndStaffFromNonRoot() {
        UUID target = UUID.randomUUID();
        AdminActor.Actor staff = new AdminActor.Actor(UUID.randomUUID(), "staff@x.test", false);
        AdminActor.Actor root = new AdminActor.Actor(UUID.randomUUID(), "root@x.test", true);

        assertThatThrownBy(() -> AccountsAdmin.requireActionAllowed(row(target, "ROOT"), root))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> AccountsAdmin.requireActionAllowed(row(target, "STAFF"), staff))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> AccountsAdmin.requireActionAllowed(row(staff.accountId(), "CUSTOMER"), staff))
                .isInstanceOf(ForbiddenException.class);
        assertThatCode(() -> AccountsAdmin.requireActionAllowed(row(target, "STAFF"), root)).doesNotThrowAnyException();
        assertThatCode(() -> AccountsAdmin.requireActionAllowed(row(target, "CUSTOMER"), staff))
                .doesNotThrowAnyException();
    }

    private static AccountsAdmin.AccountRow row(UUID id, String type) {
        return new AccountsAdmin.AccountRow(id, "x@x.test", "X", type, "ACTIVE", null, null, 0, 0);
    }
}
