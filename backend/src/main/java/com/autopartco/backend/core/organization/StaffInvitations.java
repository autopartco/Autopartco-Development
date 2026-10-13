package com.autopartco.backend.core.organization;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.internal.StaffInvitation;
import com.autopartco.backend.core.organization.internal.StaffInvitationRepository;
import com.autopartco.backend.core.organization.internal.Store;
import com.autopartco.backend.core.organization.internal.StorePermission;
import com.autopartco.backend.core.organization.internal.StorePermissionRepository;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.core.organization.internal.StoreRole;
import com.autopartco.backend.core.organization.internal.StoreRoleRepository;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/**
 * Invitaciones a colaboradores de un almacén. Genera el token de un solo uso y guarda solo su hash SHA-256; el
 * token en claro solo sale en {@link Issued} para construir el enlace del correo. No resuelve permisos de usuario
 * ni cupos de plan: lo hacen los casos de uso de {@code storesuite.staff} antes de llamar.
 */
@Service
@AllArgsConstructor
public class StaffInvitations {
    private static final int INVITATION_VALID_DAYS = 7;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StaffInvitationRepository invitationRepository;
    private final StoreRepository storeRepository;
    private final StoreRoleRepository roleRepository;
    private final StorePermissionRepository permissionRepository;

    /** Invitación recién creada o renovada junto con su token en claro (solo para el enlace del correo). */
    public record Issued(StaffInvitationView invitation, String rawToken) {
    }

    /** Invitaciones sin aceptar ni revocar del almacén, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<StaffInvitationView> listOpen(UUID storeId) {
        return invitationRepository
                .findByStore_IdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(storeId)
                .stream()
                .map(this::toView)
                .toList();
    }

    /** Invitaciones abiertas que aún no vencen; ocupan cupo de usuarios del plan. */
    @Transactional(readOnly = true)
    public long countPending(UUID storeId) {
        return invitationRepository
                .countByStore_IdAndAcceptedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(
                        storeId,
                        LocalDateTime.now()
                );
    }

    /** @throws ConflictException {@code SELLER_COLLABORATOR_INVITATION_PENDING} si ya hay una abierta para el correo */
    @Transactional(readOnly = true)
    public void requireNoneOpenFor(UUID storeId, String normalizedEmail) {
        if (invitationRepository
                .findByStore_IdAndEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNull(storeId, normalizedEmail)
                .isPresent()) {
            throw new ConflictException(
                    "SELLER_COLLABORATOR_INVITATION_PENDING",
                    "Ya existe una invitación pendiente para este correo; puedes reenviarla."
            );
        }
    }

    /**
     * Crea un rol propio del almacén con los permisos indicados y una invitación válida por 7 días. El llamador
     * ya bloqueó el almacén y validó los permisos con {@link StaffDirectory#requireAssignable}.
     */
    @Transactional
    public Issued create(
            UUID storeId,
            UUID invitedByAccountId,
            String normalizedEmail,
            String roleName,
            Set<String> permissionCodes
    ) {
        Store store = storeRepository.getReferenceById(storeId);
        List<StorePermission> permissions = permissionRepository.findByCodeIn(permissionCodes);
        String rawToken = newToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(INVITATION_VALID_DAYS);
        StoreRole role = roleRepository.save(StoreRole.builder()
                .store(store)
                .code("INVITED_" + UUID.randomUUID().toString().replace("-", ""))
                .name(roleName.trim())
                .permissions(new HashSet<>(permissions))
                .build());
        StaffInvitation invitation = invitationRepository.save(StaffInvitation.builder()
                .store(store)
                .invitedByAccountId(invitedByAccountId)
                .role(role)
                .email(normalizedEmail)
                .tokenHash(hash(rawToken))
                .expiresAt(expiresAt)
                .build());
        return new Issued(toView(invitation), rawToken);
    }

    /**
     * Invitación del almacén que sigue abierta (sin aceptar ni revocar).
     *
     * @throws NotFoundException {@code SELLER_COLLABORATOR_INVITATION_NOT_FOUND}
     * @throws ConflictException {@code SELLER_COLLABORATOR_INVITATION_CLOSED}
     */
    @Transactional(readOnly = true)
    public StaffInvitationView requireOpen(UUID storeId, UUID invitationId) {
        StaffInvitation invitation = findInStore(storeId, invitationId);
        if (invitation.getAcceptedAt() != null || invitation.getRevokedAt() != null) {
            throw new ConflictException(
                    "SELLER_COLLABORATOR_INVITATION_CLOSED",
                    "Esta invitación ya fue aceptada o revocada."
            );
        }
        return toView(invitation);
    }

    /** Sustituye el token (el anterior deja de valer) y extiende la vigencia 7 días desde {@code now}. */
    @Transactional
    public Issued renew(UUID storeId, UUID invitationId, LocalDateTime now) {
        StaffInvitation invitation = findInStore(storeId, invitationId);
        String rawToken = newToken();
        invitation.setTokenHash(hash(rawToken));
        invitation.setExpiresAt(now.plusDays(INVITATION_VALID_DAYS));
        return new Issued(toView(invitationRepository.save(invitation)), rawToken);
    }

    /**
     * Revoca la invitación y borra su token. Es idempotente si ya estaba revocada.
     *
     * @throws ConflictException {@code SELLER_COLLABORATOR_INVITATION_ACCEPTED} si ya fue aceptada
     */
    @Transactional
    public void revoke(UUID storeId, UUID invitationId) {
        StaffInvitation invitation = findInStore(storeId, invitationId);
        if (invitation.getAcceptedAt() != null) {
            throw new ConflictException(
                    "SELLER_COLLABORATOR_INVITATION_ACCEPTED",
                    "La invitación ya fue aceptada."
            );
        }
        if (invitation.getRevokedAt() == null) {
            invitation.setRevokedAt(LocalDateTime.now());
            invitation.setTokenHash(null);
            invitationRepository.save(invitation);
        }
    }

    /** Invitación pendiente y vigente para el token en claro, sin bloquear (vista previa pública). */
    @Transactional(readOnly = true)
    public StaffInvitationView findPendingByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invitationNotFound();
        }
        StaffInvitation invitation = invitationRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> invitationNotFound());
        validatePendingInvitation(invitation);
        return toView(invitation);
    }

    /**
     * Bloquea el almacén y luego la invitación del token (en ese orden, como el resto de operaciones del equipo) y
     * exige que siga pendiente y vigente. Para aceptar la invitación dentro de la transacción del llamador.
     */
    @Transactional
    public StaffInvitationView lockPendingByToken(String rawToken) {
        String tokenHash = hash(rawToken);
        StaffInvitation initial = invitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> invitationNotFound());
        storeRepository.findByIdForUpdate(initial.getStore().getId())
                .orElseThrow(() -> new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén."));
        StaffInvitation invitation = invitationRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> invitationNotFound());
        validatePendingInvitation(invitation);
        return toView(invitation);
    }

    /** Marca la invitación como aceptada y borra su token (un solo uso). */
    @Transactional
    public void markAccepted(UUID invitationId) {
        StaffInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> invitationNotFound());
        invitation.setAcceptedAt(LocalDateTime.now());
        invitation.setTokenHash(null);
        invitationRepository.save(invitation);
    }

    private StaffInvitation findInStore(UUID storeId, UUID invitationId) {
        return invitationRepository.findByIdAndStore_Id(invitationId, storeId)
                .orElseThrow(() -> invitationNotFound());
    }

    private void validatePendingInvitation(StaffInvitation invitation) {
        if (invitation.getAcceptedAt() != null || invitation.getRevokedAt() != null) {
            throw new BadRequestException(
                    "SELLER_COLLABORATOR_INVITATION_CLOSED",
                    "La invitación ya fue utilizada o revocada."
            );
        }
        if (!invitation.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BadRequestException(
                    "SELLER_COLLABORATOR_INVITATION_EXPIRED",
                    "La invitación venció; solicita que el administrador la reenvíe."
            );
        }
    }

    private StaffInvitationView toView(StaffInvitation invitation) {
        Store store = invitation.getStore();
        StoreRole role = invitation.getRole();
        return new StaffInvitationView(
                invitation.getId(),
                store.getId(),
                store.getBusinessName(),
                invitation.getEmail(),
                role.getId(),
                role.getName(),
                permissionCodes(role),
                invitation.getExpiresAt(),
                invitation.getCreatedAt()
        );
    }

    private List<String> permissionCodes(StoreRole role) {
        return role.getPermissions().stream()
                .map(StorePermission::getCode)
                .sorted()
                .toList();
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private NotFoundException invitationNotFound() {
        return new NotFoundException(
                "SELLER_COLLABORATOR_INVITATION_NOT_FOUND",
                "No se encontró una invitación válida."
        );
    }
}
