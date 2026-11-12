package com.autopartco.backend.core.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.organization.internal.Store;
import com.autopartco.backend.core.organization.internal.StorePermission;
import com.autopartco.backend.core.organization.internal.StorePermissionRepository;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.core.organization.internal.StoreRole;
import com.autopartco.backend.core.organization.internal.StoreRoleRepository;
import com.autopartco.backend.shared.error.BadRequestException;

@ExtendWith(MockitoExtension.class)
class StaffRolesTest {
    @Mock
    private StoreRoleRepository roleRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private StorePermissionRepository permissionRepository;

    @InjectMocks
    private StaffRoles staffRoles;

    private final UUID storeId = UUID.randomUUID();

    @Test
    void definitionsUseOnlyKnownPermissionsAndNeverOfferOwner() throws Exception {
        Set<String> known = Arrays.stream(StorePermissionCodes.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == String.class)
                .map(field -> {
                    try {
                        return (String) field.get(null);
                    } catch (IllegalAccessException exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .collect(Collectors.toSet());
        for (StaffRoles.Definition definition : StaffRoles.DEFINITIONS) {
            assertTrue(known.containsAll(definition.permissionCodes()), definition.code());
            assertEquals(definition.permissionCodes().size(), new HashSet<>(definition.permissionCodes()).size());
        }
        assertFalse(StaffRoles.find("OWNER").isPresent());
        assertFalse(StaffRoles.DEFINITIONS.stream()
                .filter(definition -> !definition.code().equals("ADMIN"))
                .anyMatch(definition -> definition.permissionCodes().contains(StorePermissionCodes.COLLABORATOR_MANAGE)));
    }

    @Test
    void requireRejectsUnknownAndOwnerCodes() {
        assertThrows(BadRequestException.class, () -> StaffRoles.require("OWNER"));
        assertThrows(BadRequestException.class, () -> StaffRoles.require("HACKER"));
        assertThrows(BadRequestException.class, () -> StaffRoles.require(null));
        assertEquals("SALES", StaffRoles.require(" sales ").code());
    }

    @Test
    void ensureCreatesTheStoreRoleWithTheDefinitionPermissions() {
        StaffRoles.Definition viewer = StaffRoles.require("VIEWER");
        List<StorePermission> permissions = viewer.permissionCodes().stream()
                .<StorePermission>map(code -> StorePermission.builder().id(UUID.randomUUID()).code(code).build())
                .toList();
        when(permissionRepository.findByCodeIn(viewer.permissionCodes())).thenReturn(permissions);
        when(roleRepository.findByStore_IdAndCode(storeId, "VIEWER")).thenReturn(Optional.empty());
        when(storeRepository.getReferenceById(storeId)).thenReturn(Store.builder().id(storeId).build());
        when(roleRepository.save(any(StoreRole.class))).thenAnswer(invocation -> {
            StoreRole role = invocation.getArgument(0);
            role.setId(UUID.randomUUID());
            return role;
        });

        staffRoles.ensure(storeId, "viewer");

        var captor = ArgumentCaptor.forClass(StoreRole.class);
        verify(roleRepository).save(captor.capture());
        assertEquals("VIEWER", captor.getValue().getCode());
        assertEquals("Solo lectura", captor.getValue().getName());
        assertEquals(new HashSet<>(permissions), captor.getValue().getPermissions());
    }

    @Test
    void ensureResyncsAnExistingRole() {
        StaffRoles.Definition sales = StaffRoles.require("SALES");
        List<StorePermission> permissions = sales.permissionCodes().stream()
                .<StorePermission>map(code -> StorePermission.builder().id(UUID.randomUUID()).code(code).build())
                .toList();
        StoreRole existing = StoreRole.builder().id(UUID.randomUUID()).code("SALES").name("Viejo")
                .permissions(new HashSet<>()).build();
        when(permissionRepository.findByCodeIn(sales.permissionCodes())).thenReturn(permissions);
        when(roleRepository.findByStore_IdAndCode(storeId, "SALES")).thenReturn(Optional.of(existing));
        when(roleRepository.save(existing)).thenReturn(existing);

        assertEquals(existing.getId(), staffRoles.ensure(storeId, "SALES"));
        assertEquals("Ventas y pedidos", existing.getName());
        assertEquals(sales.permissionCodes().size(), existing.getPermissions().size());
    }

    @Test
    void ensureFailsWhenAPermissionIsMissingInTheDatabase() {
        StaffRoles.Definition admin = StaffRoles.require("ADMIN");
        when(permissionRepository.findByCodeIn(admin.permissionCodes())).thenReturn(List.of());

        assertThrows(BadRequestException.class, () -> staffRoles.ensure(storeId, "ADMIN"));
    }
}
