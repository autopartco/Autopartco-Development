package com.autopartco.backend.core.organization;

import static com.autopartco.backend.core.organization.StorePermissionCodes.ALERT_MANAGE;
import static com.autopartco.backend.core.organization.StorePermissionCodes.ANALYTICS_READ;
import static com.autopartco.backend.core.organization.StorePermissionCodes.BRANCH_CREATE;
import static com.autopartco.backend.core.organization.StorePermissionCodes.BRANCH_DELETE;
import static com.autopartco.backend.core.organization.StorePermissionCodes.BRANCH_READ;
import static com.autopartco.backend.core.organization.StorePermissionCodes.BRANCH_UPDATE;
import static com.autopartco.backend.core.organization.StorePermissionCodes.COLLABORATOR_MANAGE;
import static com.autopartco.backend.core.organization.StorePermissionCodes.COLLABORATOR_READ;
import static com.autopartco.backend.core.organization.StorePermissionCodes.INVENTORY_ADJUST;
import static com.autopartco.backend.core.organization.StorePermissionCodes.INVENTORY_TRANSFER;
import static com.autopartco.backend.core.organization.StorePermissionCodes.ORDER_READ;
import static com.autopartco.backend.core.organization.StorePermissionCodes.ORDER_UPDATE;
import static com.autopartco.backend.core.organization.StorePermissionCodes.PRODUCT_CREATE;
import static com.autopartco.backend.core.organization.StorePermissionCodes.PRODUCT_DELETE;
import static com.autopartco.backend.core.organization.StorePermissionCodes.PRODUCT_IMAGE_MANAGE;
import static com.autopartco.backend.core.organization.StorePermissionCodes.PRODUCT_PUBLISH;
import static com.autopartco.backend.core.organization.StorePermissionCodes.PRODUCT_READ;
import static com.autopartco.backend.core.organization.StorePermissionCodes.PRODUCT_UPDATE;
import static com.autopartco.backend.core.organization.StorePermissionCodes.PROMOTION_CREATE;
import static com.autopartco.backend.core.organization.StorePermissionCodes.PROMOTION_DELETE;
import static com.autopartco.backend.core.organization.StorePermissionCodes.PROMOTION_READ;
import static com.autopartco.backend.core.organization.StorePermissionCodes.PROMOTION_UPDATE;
import static com.autopartco.backend.core.organization.StorePermissionCodes.SHIPMENT_UPDATE;
import static com.autopartco.backend.core.organization.StorePermissionCodes.STORE_READ;
import static com.autopartco.backend.core.organization.StorePermissionCodes.STORE_UPDATE;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.internal.StorePermission;
import com.autopartco.backend.core.organization.internal.StorePermissionRepository;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.core.organization.internal.StoreRole;
import com.autopartco.backend.core.organization.internal.StoreRoleRepository;
import com.autopartco.backend.shared.error.BadRequestException;

import lombok.AllArgsConstructor;

/**
 * Roles predefinidos que se pueden asignar a un colaborador. La definición (permisos) vive en código; cada almacén
 * tiene su fila en {@code roles} con el mismo código, que se crea al primer uso y se resincroniza cada vez que se
 * asigna. {@code OWNER} no es asignable.
 */
@Service
@AllArgsConstructor
public class StaffRoles {
    public static final String OWNER = "OWNER";

    /** Definición de un rol predefinido. */
    public record Definition(String code, String name, String description, List<String> permissionCodes) {
    }

    public static final List<Definition> DEFINITIONS = List.of(
            new Definition("ADMIN", "Administrador",
                    "Gestiona todo el almacén y su equipo. No puede cambiar el plan ni la propiedad.",
                    List.of(STORE_READ, STORE_UPDATE, BRANCH_READ, BRANCH_CREATE, BRANCH_UPDATE, BRANCH_DELETE,
                            PRODUCT_READ, PRODUCT_CREATE, PRODUCT_UPDATE, PRODUCT_PUBLISH, PRODUCT_DELETE,
                            PRODUCT_IMAGE_MANAGE, INVENTORY_ADJUST, INVENTORY_TRANSFER,
                            PROMOTION_READ, PROMOTION_CREATE, PROMOTION_UPDATE, PROMOTION_DELETE,
                            ORDER_READ, ORDER_UPDATE, SHIPMENT_UPDATE, ANALYTICS_READ, ALERT_MANAGE,
                            COLLABORATOR_READ, COLLABORATOR_MANAGE)),
            new Definition("SALES", "Ventas y pedidos",
                    "Atiende pedidos y envíos; consulta productos, existencias y promociones.",
                    List.of(STORE_READ, BRANCH_READ, PRODUCT_READ, PROMOTION_READ,
                            ORDER_READ, ORDER_UPDATE, SHIPMENT_UPDATE)),
            new Definition("INVENTORY", "Inventario y productos",
                    "Crea y publica productos, ajusta existencias y transfiere entre sucursales.",
                    List.of(STORE_READ, BRANCH_READ, PRODUCT_READ, PRODUCT_CREATE, PRODUCT_UPDATE,
                            PRODUCT_PUBLISH, PRODUCT_DELETE, PRODUCT_IMAGE_MANAGE,
                            INVENTORY_ADJUST, INVENTORY_TRANSFER)),
            new Definition("VIEWER", "Solo lectura",
                    "Consulta el almacén, pedidos, productos y reportes sin poder cambiar nada.",
                    List.of(STORE_READ, BRANCH_READ, PRODUCT_READ, PROMOTION_READ, ORDER_READ,
                            ANALYTICS_READ, COLLABORATOR_READ))
    );

    private final StoreRoleRepository roleRepository;
    private final StoreRepository storeRepository;
    private final StorePermissionRepository permissionRepository;

    public static Optional<Definition> find(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return DEFINITIONS.stream().filter(definition -> definition.code().equals(normalized)).findFirst();
    }

    /** @throws BadRequestException {@code SELLER_COLLABORATOR_ROLE_INVALID} si el código no es un rol asignable */
    public static Definition require(String code) {
        return find(code).orElseThrow(() -> new BadRequestException(
                "SELLER_COLLABORATOR_ROLE_INVALID",
                "Elige uno de los roles disponibles."
        ));
    }

    /**
     * Fila del rol predefinido en el almacén, creada si falta y con sus permisos alineados con la definición.
     *
     * @return id del rol
     */
    @Transactional
    public UUID ensure(UUID storeId, String code) {
        Definition definition = require(code);
        Set<StorePermission> permissions = new HashSet<>(permissionRepository.findByCodeIn(definition.permissionCodes()));
        if (permissions.size() != definition.permissionCodes().size()) {
            throw new BadRequestException(
                    "SELLER_COLLABORATOR_PERMISSION_NOT_CONFIGURED",
                    "Uno o más permisos del rol no están configurados en el sistema."
            );
        }
        StoreRole role = roleRepository.findByStore_IdAndCode(storeId, definition.code())
                .orElseGet(() -> StoreRole.builder()
                        .store(storeRepository.getReferenceById(storeId))
                        .code(definition.code())
                        .build());
        role.setName(definition.name());
        role.setPermissions(permissions);
        return roleRepository.save(role).getId();
    }
}
