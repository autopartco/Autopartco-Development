package com.autopartco.backend.core.organization;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.internal.Collaborator;
import com.autopartco.backend.core.organization.internal.CollaboratorRepository;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.core.organization.internal.StoreRoleRepository;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.persistence.StateCodes;
import com.autopartco.backend.shared.persistence.StateRepository;

import lombok.AllArgsConstructor;

/**
 * Directorio de colaboradores de un almacén: miembros, alta, cambio de rol, activación y conteo de activos. No
 * resuelve permisos de usuario: lo hacen los casos de uso con {@link StoreAccess}.
 * {@link #listMembers} es un read model (§4.2.5) sobre {@code collaborators}, {@code persons},
 * {@code accounts}, {@code roles*}, {@code permissions} y {@code states}.
 */
@Service
@AllArgsConstructor
public class StaffDirectory {
    private final JdbcTemplate jdbcTemplate;
    private final CollaboratorRepository collaboratorRepository;
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
                       COALESCE(string_agg(DISTINCT role.code, ',' ORDER BY role.code), '') AS role_codes,
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
                """, (result, row) -> StaffMember.of(
                result.getObject("id", UUID.class),
                result.getString("name"),
                result.getString("surname"),
                result.getString("email"),
                result.getString("status"),
                splitCodes(result.getString("role_names")),
                splitCodes(result.getString("permissions")),
                splitCodes(result.getString("role_codes"))
        ), storeId);
    }

    /**
     * Correos (en minúsculas, sin repetir) de los colaboradores activos del almacén cuyos roles tienen el permiso.
     * Solo para avisos operativos del propio almacén; no se devuelve a ningún cliente de la API.
     */
    @Transactional(readOnly = true)
    public List<String> emailsWithPermission(UUID storeId, String permissionCode) {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT lower(account.email)
                FROM collaborators collaborator
                JOIN states state ON state.id = collaborator.state AND state.code = 'ACTIVE'
                JOIN accounts account ON account.id_person = collaborator.person_id
                                     AND account.deleted_at IS NULL
                JOIN roles_collaborators role_link ON role_link.collaborator_id = collaborator.id
                                                  AND role_link.deleted_at IS NULL
                JOIN roles_permissions role_permission ON role_permission.role_id = role_link.role_id
                                                      AND role_permission.deleted_at IS NULL
                JOIN permissions permission ON permission.id = role_permission.permission_id
                                           AND permission.deleted_at IS NULL
                WHERE collaborator.collab_store_id = ?
                  AND collaborator.deleted_at IS NULL
                  AND permission.code = ?
                ORDER BY 1
                """, String.class, storeId, permissionCode);
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

    /**
     * Colaborador del almacén que la persona {@code actorPersonId} puede administrar: no es el propietario ni la
     * propia persona.
     *
     * @throws NotFoundException {@code SELLER_COLLABORATOR_NOT_FOUND} si no pertenece al almacén
     * @throws ConflictException {@code SELLER_COLLABORATOR_OWNER} o {@code SELLER_COLLABORATOR_SELF}
     */
    private Collaborator requireManageable(UUID storeId, UUID memberId, UUID actorPersonId) {
        Collaborator collaborator = collaboratorRepository.findByIdAndStore_Id(memberId, storeId)
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_COLLABORATOR_NOT_FOUND",
                        "No se encontró el colaborador en este almacén."
                ));
        if (collaborator.getRoles().stream().anyMatch(role -> StaffRoles.OWNER.equals(role.getCode()))) {
            throw new ConflictException(
                    "SELLER_COLLABORATOR_OWNER",
                    "No se puede cambiar el rol ni el acceso del propietario del almacén."
            );
        }
        if (collaborator.getPersonId().equals(actorPersonId)) {
            throw new ConflictException(
                    "SELLER_COLLABORATOR_SELF",
                    "No puedes cambiar tu propio rol ni tu acceso; pídeselo al propietario."
            );
        }
        return collaborator;
    }

    /** Reemplaza los roles del colaborador por el rol indicado (ya creado con {@link StaffRoles#ensure}). */
    @Transactional
    public void changeRole(UUID storeId, UUID memberId, UUID actorPersonId, UUID roleId) {
        Collaborator collaborator = requireManageable(storeId, memberId, actorPersonId);
        collaborator.setRoles(new HashSet<>(Set.of(roleRepository.getReferenceById(roleId))));
        collaboratorRepository.save(collaborator);
    }

    /**
     * Activa o desactiva al colaborador. Desactivado conserva su rol, pero pierde todo acceso al almacén.
     *
     * @return {@code true} si el estado cambió
     */
    @Transactional
    public boolean setActive(UUID storeId, UUID memberId, UUID actorPersonId, boolean active) {
        Collaborator collaborator = requireManageable(storeId, memberId, actorPersonId);
        String target = active ? StateCodes.ACTIVE : StateCodes.INACTIVE;
        if (target.equals(collaborator.getState().getCode())) {
            return false;
        }
        collaborator.setState(findState(target));
        collaboratorRepository.save(collaborator);
        return true;
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
