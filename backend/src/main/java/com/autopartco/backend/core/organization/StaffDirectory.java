package com.autopartco.backend.core.organization;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.internal.Collaborator;
import com.autopartco.backend.core.organization.internal.CollaboratorRepository;
import com.autopartco.backend.core.organization.internal.StorePermission;
import com.autopartco.backend.core.organization.internal.StorePermissionRepository;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.core.organization.internal.StoreRoleRepository;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.persistence.StateCodes;
import com.autopartco.backend.shared.persistence.StateRepository;

import lombok.AllArgsConstructor;

/**
 * Directorio de colaboradores de un almacén: miembros, permisos asignables a invitados, alta de colaborador y
 * conteo de activos. No resuelve permisos de usuario: lo hacen los casos de uso con {@link StoreAccess}.
 * {@link #listMembers} es un read model (§4.2.5) sobre {@code collaborators}, {@code persons},
 * {@code accounts}, {@code roles*}, {@code permissions} y {@code states}.
 */
@Service
@AllArgsConstructor
public class StaffDirectory {
    private static final Set<String> ASSIGNABLE_PERMISSIONS = Set.of(
            StorePermissionCodes.STORE_READ,
            StorePermissionCodes.BRANCH_READ,
            StorePermissionCodes.BRANCH_CREATE,
            StorePermissionCodes.BRANCH_UPDATE,
            StorePermissionCodes.BRANCH_DELETE,
            StorePermissionCodes.PRODUCT_READ,
            StorePermissionCodes.PRODUCT_CREATE,
            StorePermissionCodes.PRODUCT_UPDATE,
            StorePermissionCodes.PRODUCT_PUBLISH,
            StorePermissionCodes.PRODUCT_DELETE,
            StorePermissionCodes.PRODUCT_IMAGE_MANAGE,
            StorePermissionCodes.PROMOTION_READ,
            StorePermissionCodes.PROMOTION_CREATE,
            StorePermissionCodes.PROMOTION_UPDATE,
            StorePermissionCodes.PROMOTION_DELETE,
            StorePermissionCodes.ORDER_READ,
            StorePermissionCodes.ORDER_UPDATE,
            StorePermissionCodes.SHIPMENT_UPDATE
    );

    private final JdbcTemplate jdbcTemplate;
    private final CollaboratorRepository collaboratorRepository;
    private final StorePermissionRepository permissionRepository;
    private final StoreRepository storeRepository;
    private final StoreRoleRepository roleRepository;
    private final StateRepository stateRepository;

    @Transactional(readOnly = true)
    public long countActive(UUID storeId) {
        return collaboratorRepository.countActiveInStore(storeId);
    }

    @Transactional(readOnly = true)
    public List<StaffMember> listMembers(UUID storeId) {
        return jdbcTemplate.query("""
                SELECT collaborator.id,
                       person.name,
                       person.surname,
                       account.email,
                       state.code AS status,
                       COALESCE(string_agg(DISTINCT role.name, ',' ORDER BY role.name), '') AS role_names,
                       COALESCE(string_agg(DISTINCT permission.code, ',' ORDER BY permission.code), '') AS permissions
                FROM collaborators collaborator
                JOIN persons person ON person.id = collaborator.person_id
                JOIN accounts account ON account.id_person = person.id
                JOIN states state ON state.id = collaborator.state
                LEFT JOIN roles_collaborators role_link
                       ON role_link.collaborator_id = collaborator.id
                      AND role_link.deleted_at IS NULL
                LEFT JOIN roles role
                       ON role.id = role_link.role_id
                      AND role.deleted_at IS NULL
                LEFT JOIN roles_permissions role_permission
                       ON role_permission.role_id = role.id
                      AND role_permission.deleted_at IS NULL
                LEFT JOIN permissions permission
                       ON permission.id = role_permission.permission_id
                      AND permission.deleted_at IS NULL
                WHERE collaborator.collab_store_id = ?
                  AND collaborator.deleted_at IS NULL
                  AND account.deleted_at IS NULL
                GROUP BY collaborator.id, person.name, person.surname, account.email, state.code
                ORDER BY person.name, person.surname, account.email
                """, (result, row) -> new StaffMember(
                result.getObject("id", UUID.class),
                result.getString("name"),
                result.getString("surname"),
                result.getString("email"),
                result.getString("status"),
                splitCodes(result.getString("role_names")),
                splitCodes(result.getString("permissions"))
        ), storeId);
    }

    /** Permisos que se pueden asignar a un colaborador invitado, ordenados por código. */
    @Transactional(readOnly = true)
    public List<PermissionOption> assignablePermissions() {
        return permissionRepository.findByCodeIn(ASSIGNABLE_PERMISSIONS).stream()
                .sorted((first, second) -> first.getCode().compareTo(second.getCode()))
                .map(permission -> new PermissionOption(permission.getCode(), permission.getDescription()))
                .toList();
    }

    /**
     * Normaliza los códigos pedidos (recorte y mayúsculas) y exige que todos sean asignables y estén configurados.
     *
     * @return los códigos normalizados, ordenados y sin repetidos
     */
    @Transactional(readOnly = true)
    public Set<String> requireAssignable(List<String> requestedCodes) {
        Set<String> normalized = requestedCodes.stream()
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(TreeSet::new));
        if (normalized.isEmpty() || !ASSIGNABLE_PERMISSIONS.containsAll(normalized)) {
            throw new BadRequestException(
                    "SELLER_COLLABORATOR_PERMISSION_INVALID",
                    "Selecciona permisos válidos para el colaborador."
            );
        }
        List<StorePermission> permissions = permissionRepository.findByCodeIn(normalized);
        if (permissions.size() != normalized.size()) {
            throw new BadRequestException(
                    "SELLER_COLLABORATOR_PERMISSION_NOT_CONFIGURED",
                    "Uno o más permisos no están configurados en el sistema."
            );
        }
        return normalized;
    }

    @Transactional(readOnly = true)
    public boolean isMember(UUID personId, UUID storeId) {
        return collaboratorRepository.existsInStore(personId, storeId);
    }

    /**
     * Da de alta a la persona como colaboradora activa del almacén con el rol indicado.
     *
     * @throws ConflictException {@code SELLER_COLLABORATOR_ALREADY_EXISTS} si ya es colaboradora
     */
    @Transactional
    public void addCollaborator(UUID storeId, UUID personId, UUID roleId) {
        if (collaboratorRepository.existsInStore(personId, storeId)) {
            throw new ConflictException(
                    "SELLER_COLLABORATOR_ALREADY_EXISTS",
                    "Esta cuenta ya es colaboradora del almacén."
            );
        }
        collaboratorRepository.save(Collaborator.builder()
                .personId(personId)
                // El almacén y el rol ya están cargados (y el almacén bloqueado) en la transacción del llamador.
                .store(storeRepository.getReferenceById(storeId))
                .state(findState(StateCodes.ACTIVE))
                .roles(Set.of(roleRepository.getReferenceById(roleId)))
                .build());
    }

    private List<String> splitCodes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(","));
    }

    private State findState(String code) {
        return stateRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException(
                        "STATE_NOT_FOUND",
                        "No está configurado el estado " + code + "."
                ));
    }
}
