package com.autopartco.backend.seller.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.autopartco.backend.core.identity.internal.Account;
import com.autopartco.backend.core.identity.internal.AccountType;
import com.autopartco.backend.core.identity.internal.Person;
import com.autopartco.backend.core.identity.internal.AccountRepository;
import com.autopartco.backend.core.identity.internal.PersonRepository;
import com.autopartco.backend.shared.persistence.StateRepository;
import com.autopartco.backend.core.identity.internal.AccountTypeRepository;
import com.autopartco.backend.core.notification.EmailService;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.config.AppProperties;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.seller.dto.SellerCollaboratorAcceptRequest;
import com.autopartco.backend.seller.dto.SellerCollaboratorInviteRequest;
import com.autopartco.backend.seller.dto.SellerCollaboratorInvitationResponse;
import com.autopartco.backend.seller.dto.SellerCollaboratorResponse;
import com.autopartco.backend.seller.dto.SellerPermissionOption;
import com.autopartco.backend.core.organization.internal.StaffInvitation;
import com.autopartco.backend.core.organization.internal.StaffInvitationRepository;
import com.autopartco.backend.seller.service.SellerPlanEntitlementService.Capacity;
import com.autopartco.backend.core.organization.internal.Collaborator;
import com.autopartco.backend.core.organization.internal.Store;
import com.autopartco.backend.core.organization.internal.StorePermission;
import com.autopartco.backend.core.organization.internal.StoreRole;
import com.autopartco.backend.core.organization.internal.CollaboratorRepository;
import com.autopartco.backend.core.organization.internal.StorePermissionRepository;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.core.organization.internal.StoreRoleRepository;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class SellerCollaboratorService {
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
    private static final String ACTIVE_STATE = "ACTIVE";
    private static final String LOCKED_STATE = "LOCKED";
    private static final String STORE_MANAGER_ACCOUNT_TYPE = "STORE_MANAGER";
    private static final int INVITATION_VALID_DAYS = 7;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final AccountRepository accountRepository;
    private final PersonRepository personRepository;
    private final StateRepository stateRepository;
    private final AccountTypeRepository accountTypeRepository;
    private final StoreRepository storeRepository;
    private final StoreRoleRepository roleRepository;
    private final StorePermissionRepository permissionRepository;
    private final CollaboratorRepository collaboratorRepository;
    private final StaffInvitationRepository invitationRepository;
    private final SellerPlanEntitlementService entitlementService;
    private final StoreAccess storeAccessService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public List<SellerPermissionOption> permissionOptions(UUID storeId, String email) {
        storeAccessService.requirePermission(email, storeId, StorePermissionCodes.COLLABORATOR_MANAGE);
        return permissionRepository.findByCodeIn(ASSIGNABLE_PERMISSIONS).stream()
                .sorted((first, second) -> first.getCode().compareTo(second.getCode()))
                .map(permission -> new SellerPermissionOption(permission.getCode(), permission.getDescription()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SellerCollaboratorResponse list(UUID storeId, String email) {
        storeAccessService.requirePermission(email, storeId, StorePermissionCodes.COLLABORATOR_READ);
        List<SellerCollaboratorResponse.Member> members = jdbcTemplate.query("""
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
                """, (result, row) -> new SellerCollaboratorResponse.Member(
                result.getObject("id", UUID.class),
                result.getString("name"),
                result.getString("surname"),
                result.getString("email"),
                result.getString("status"),
                splitCodes(result.getString("role_names")),
                splitCodes(result.getString("permissions"))
        ), storeId);
        List<SellerCollaboratorInvitationResponse> invitations = invitationRepository
                .findByStore_IdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(storeId)
                .stream()
                .map(this::toInvitationResponse)
                .toList();
        return new SellerCollaboratorResponse(storeId, members, invitations);
    }

    @Transactional
    public SellerCollaboratorInvitationResponse invite(
            UUID storeId,
            String email,
            SellerCollaboratorInviteRequest request
    ) {
        storeAccessService.requirePermission(email, storeId, StorePermissionCodes.COLLABORATOR_MANAGE);
        Store store = lockStore(storeId);
        String normalizedEmail = normalizeEmail(request.email());
        if (accountRepository.findByEmailIgnoreCase(normalizedEmail)
                .map(account -> collaboratorRepository.existsByPersonIdAndStore_Id(
                        account.getPerson().getId(),
                        storeId
                ))
                .orElse(false)) {
            throw new ConflictException(
                    "SELLER_COLLABORATOR_ALREADY_EXISTS",
                    "Esta cuenta ya es colaboradora del almacén."
            );
        }
        if (invitationRepository
                .findByStore_IdAndEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNull(storeId, normalizedEmail)
                .isPresent()) {
            throw new ConflictException(
                    "SELLER_COLLABORATOR_INVITATION_PENDING",
                    "Ya existe una invitación pendiente para este correo; puedes reenviarla."
            );
        }
        List<StorePermission> permissions = resolvePermissions(request.permissionCodes());
        reserveUserCapacity(storeId, 1);
        Account inviter = findAccount(email);
        String rawToken = newToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(INVITATION_VALID_DAYS);
        StoreRole role = roleRepository.save(StoreRole.builder()
                .store(store)
                .code("INVITED_" + UUID.randomUUID().toString().replace("-", ""))
                .name(request.roleName().trim())
                .permissions(new HashSet<>(permissions))
                .build());
        StaffInvitation invitation = invitationRepository.save(StaffInvitation.builder()
                .store(store)
                .invitedByAccountId(inviter.getId())
                .role(role)
                .email(normalizedEmail)
                .tokenHash(hash(rawToken))
                .expiresAt(expiresAt)
                .build());
        sendInvitation(invitation, rawToken);
        return toInvitationResponse(invitation);
    }

    @Transactional
    public SellerCollaboratorInvitationResponse resend(
            UUID storeId,
            UUID invitationId,
            String email
    ) {
        storeAccessService.requirePermission(email, storeId, StorePermissionCodes.COLLABORATOR_MANAGE);
        lockStore(storeId);
        StaffInvitation invitation = invitationRepository.findByIdAndStore_Id(invitationId, storeId)
                .orElseThrow(() -> invitationNotFound());
        if (invitation.getAcceptedAt() != null || invitation.getRevokedAt() != null) {
            throw new ConflictException(
                    "SELLER_COLLABORATOR_INVITATION_CLOSED",
                    "Esta invitación ya fue aceptada o revocada."
            );
        }
        LocalDateTime now = LocalDateTime.now();
        reserveUserCapacity(storeId, invitation.getExpiresAt().isAfter(now) ? 0 : 1);
        String rawToken = newToken();
        invitation.setTokenHash(hash(rawToken));
        invitation.setExpiresAt(now.plusDays(INVITATION_VALID_DAYS));
        sendInvitation(invitation, rawToken);
        return toInvitationResponse(invitationRepository.save(invitation));
    }

    @Transactional
    public void revoke(UUID storeId, UUID invitationId, String email) {
        storeAccessService.requirePermission(email, storeId, StorePermissionCodes.COLLABORATOR_MANAGE);
        lockStore(storeId);
        StaffInvitation invitation = invitationRepository.findByIdAndStore_Id(invitationId, storeId)
                .orElseThrow(() -> invitationNotFound());
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

    @Transactional(readOnly = true)
    public SellerCollaboratorResponse.InvitationPreview preview(String rawToken) {
        StaffInvitation invitation = findPendingInvitation(rawToken);
        Store store = invitation.getStore();
        boolean accountExists = accountRepository.existsByEmailIgnoreCase(invitation.getEmail());
        return new SellerCollaboratorResponse.InvitationPreview(
                store.getId(),
                store.getBusinessName(),
                invitation.getEmail(),
                invitation.getRole().getName(),
                permissionCodes(invitation.getRole()),
                !accountExists,
                invitation.getExpiresAt()
        );
    }

    @Transactional
    public SellerCollaboratorResponse.Accepted accept(SellerCollaboratorAcceptRequest request) {
        String tokenHash = hash(request.token());
        StaffInvitation initial = invitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> invitationNotFound());
        Store store = lockStore(initial.getStore().getId());
        StaffInvitation invitation = invitationRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> invitationNotFound());
        validatePendingInvitation(invitation);
        reserveUserCapacity(store.getId(), 0);

        Account account = accountRepository.findByEmailIgnoreCaseForUpdate(invitation.getEmail())
                .orElseGet(() -> createInvitedAccount(invitation.getEmail(), request.account()));
        if (LOCKED_STATE.equals(account.getState().getCode())) {
            throw new ConflictException(
                    "SELLER_COLLABORATOR_ACCOUNT_LOCKED",
                    "La cuenta está bloqueada y no puede aceptar invitaciones."
            );
        }
        if (!ACTIVE_STATE.equals(account.getState().getCode())) {
            account.setState(findState(ACTIVE_STATE));
        }
        if (collaboratorRepository.existsByPersonIdAndStore_Id(account.getPerson().getId(), store.getId())) {
            throw new ConflictException(
                    "SELLER_COLLABORATOR_ALREADY_EXISTS",
                    "Esta cuenta ya es colaboradora del almacén."
            );
        }
        collaboratorRepository.save(Collaborator.builder()
                .personId(account.getPerson().getId())
                .store(store)
                .state(findState(ACTIVE_STATE))
                .roles(Set.of(invitation.getRole()))
                .build());

        invitation.setAcceptedAt(LocalDateTime.now());
        invitation.setTokenHash(null);
        invitationRepository.save(invitation);
        return new SellerCollaboratorResponse.Accepted(
                store.getId(),
                store.getBusinessName(),
                account.getEmail(),
                invitation.getRole().getName()
        );
    }

    private Store lockStore(UUID storeId) {
        return storeRepository.findByIdForUpdate(storeId)
                .orElseThrow(() -> new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén."));
    }

    private void reserveUserCapacity(UUID storeId, long invitationsToReserve) {
        long pendingInvitations = invitationRepository
                .countByStore_IdAndAcceptedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(
                        storeId,
                        LocalDateTime.now()
                );
        entitlementService.requireCapability(storeId, "CUSTOM_ROLES");
        entitlementService.requireCapacity(storeId, Capacity.USERS, pendingInvitations + invitationsToReserve);
    }

    private List<StorePermission> resolvePermissions(List<String> requestedCodes) {
        Set<String> normalized = requestedCodes.stream()
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
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
        return permissions;
    }

    private StaffInvitation findPendingInvitation(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invitationNotFound();
        }
        StaffInvitation invitation = invitationRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> invitationNotFound());
        validatePendingInvitation(invitation);
        return invitation;
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

    private Account createInvitedAccount(
            String email,
            SellerCollaboratorAcceptRequest.AccountDetails details
    ) {
        if (details == null) {
            throw new BadRequestException(
                    "SELLER_COLLABORATOR_ACCOUNT_REQUIRED",
                    "Completa tus datos personales y crea una contraseña para aceptar la invitación."
            );
        }
        String identification = details.identification().trim();
        if (personRepository.existsByIdentification(identification)) {
            throw new ConflictException(
                    "IDENTIFICATION_ALREADY_EXISTS",
                    "Ya existe una persona registrada con esta identificación."
            );
        }
        AccountType accountType = accountTypeRepository.findByCode(STORE_MANAGER_ACCOUNT_TYPE)
                .orElseThrow(() -> new NotFoundException(
                        "ACCOUNT_TYPE_NOT_FOUND",
                        "No existe el tipo de cuenta de vendedor."
                ));
        Person person = personRepository.save(Person.builder()
                .name(details.name().trim())
                .surname(details.surname().trim())
                .identification(identification)
                .dateBirth(details.dateBirth())
                .numberPhone(details.numberPhone().trim())
                .build());
        Account account = Account.builder()
                .person(person)
                .email(normalizeEmail(email))
                .password(passwordEncoder.encode(details.password()))
                .accountType(accountType)
                .state(findState(ACTIVE_STATE))
                .configuration(java.util.Map.of())
                .build();
        return accountRepository.save(account);
    }

    private void sendInvitation(StaffInvitation invitation, String rawToken) {
        String link = UriComponentsBuilder
                .fromUriString(appProperties.frontendBaseUrl().replaceAll("/+$", ""))
                .path("/seller/invitation")
                .queryParam("token", rawToken)
                .build()
                .toUriString();
        emailService.sendSellerCollaboratorInvitation(
                invitation.getEmail(),
                invitation.getStore().getBusinessName(),
                invitation.getRole().getName(),
                link,
                invitation.getExpiresAt()
        );
    }

    private SellerCollaboratorInvitationResponse toInvitationResponse(
            StaffInvitation invitation
    ) {
        return new SellerCollaboratorInvitationResponse(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getRole().getName(),
                permissionCodes(invitation.getRole()),
                invitation.getExpiresAt(),
                !invitation.getExpiresAt().isAfter(LocalDateTime.now()),
                invitation.getCreatedAt()
        );
    }

    private List<String> permissionCodes(StoreRole role) {
        return role.getPermissions().stream()
                .map(StorePermission::getCode)
                .sorted()
                .toList();
    }

    private List<String> splitCodes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(","));
    }

    private Account findAccount(String email) {
        return accountRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "No se encontró la cuenta autenticada."));
    }

    private State findState(String code) {
        return stateRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException(
                        "STATE_NOT_FOUND",
                        "No está configurado el estado " + code + "."
                ));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
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
