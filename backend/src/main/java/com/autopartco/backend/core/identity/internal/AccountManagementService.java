package com.autopartco.backend.core.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.identity.internal.social.AccountIdentity;
import com.autopartco.backend.core.identity.internal.social.AccountIdentityRepository;
import com.autopartco.backend.core.notification.NotificationKind;
import com.autopartco.backend.core.notification.NotificationService;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.error.TooManyRequestsException;

import lombok.AllArgsConstructor;

/**
 * Administración de la cuenta común a todos los roles: contraseña, correo (con código al correo nuevo), métodos de
 * ingreso vinculados, exportación de datos (Ley 1581, derecho de acceso) y solicitud de eliminación.
 */
@Service
@AllArgsConstructor
public class AccountManagementService {
    static final Duration CODE_VALIDITY = Duration.ofMinutes(15);
    static final int MAX_ATTEMPTS = 5;
    static final int COOLDOWN_SECONDS = 60;
    static final int MAX_PER_HOUR = 5;
    static final String EMAIL_CHANGE = EmailVerificationTokenService.PURPOSE_EMAIL_CHANGE;

    private final AccountRepository accountRepository;
    private final AccountTokenRepository accountTokenRepository;
    private final EmailVerificationTokenService tokenService;
    private final TokenMapper tokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final AccountSessionService accountSessionService;
    private final AccountIdentityRepository identityRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final JdbcTemplate jdbcTemplate;

    public record LinkedIdentity(String provider, String email, LocalDateTime linkedAt) {
    }

    public record EmailChangeRequested(boolean sent, long retryAfterSeconds, long codeValidMinutes) {
    }

    public record DeletionRequest(UUID id, String status, String reason, LocalDateTime createdAt) {
    }

    // ------------------------------------------------------------------ contraseña

    /**
     * Cambia la contraseña. Cierra las demás sesiones ({@code currentRefreshToken}: la sesión actual se conserva; sin él,
     * se cierran todas). Cuentas creadas con Google no conocen su contraseña: deben definirla con la recuperación.
     */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword, String currentRefreshToken) {
        Account account = lock(email);
        requirePassword(account, currentPassword);
        if (passwordEncoder.matches(newPassword, account.getPassword())) {
            throw new BadRequestException("PASSWORD_UNCHANGED", "La nueva contraseña debe ser distinta de la actual.");
        }
        account.setPassword(passwordEncoder.encode(newPassword));
        accountRepository.save(account);
        if (currentRefreshToken != null && !currentRefreshToken.isBlank()) {
            accountSessionService.revokeOthersByRefreshToken(currentRefreshToken, account.getEmail());
        } else {
            accountSessionService.revokeAll(account.getId());
        }
        auditService.record(account.getId(), "PASSWORD_CHANGED", "ACCOUNT", account.getId(), Map.of());
        securityNotice(account, account.getEmail(), "Cambiaste tu contraseña",
                "La contraseña de tu cuenta de Autopartco se cambió y cerramos las demás sesiones abiertas.");
    }

    // ------------------------------------------------------------------ correo

    /**
     * Envía un código al correo nuevo. Exige la contraseña, salvo en cuentas con ingreso social (pueden no conocerla):
     * ahí basta la sesión, y el código prueba que el correo nuevo es de la persona.
     */
    @Transactional
    public EmailChangeRequested requestEmailChange(String email, String newEmail, String currentPassword) {
        Account account = lock(email);
        String target = normalize(newEmail);
        if (target.equals(account.getEmail())) {
            throw new BadRequestException("EMAIL_UNCHANGED", "Ese ya es tu correo.");
        }
        if (!identityRepository.existsByAccount_Id(account.getId()) || (currentPassword != null && !currentPassword.isBlank())) {
            requirePassword(account, currentPassword);
        }
        requireAvailable(target);

        LocalDateTime now = LocalDateTime.now();
        long wait = accountTokenRepository.findFirstByAccount_IdAndPurposeOrderByCreatedAtDesc(account.getId(), EMAIL_CHANGE)
                .map(token -> Duration.between(now, token.getCreatedAt().plusSeconds(COOLDOWN_SECONDS)).toSeconds())
                .orElse(0L);
        if (wait > 0) {
            return new EmailChangeRequested(false, wait, CODE_VALIDITY.toMinutes());
        }
        if (accountTokenRepository.countByAccount_IdAndPurposeAndCreatedAtAfter(account.getId(), EMAIL_CHANGE,
                now.minusHours(1)) >= MAX_PER_HOUR) {
            return new EmailChangeRequested(false, COOLDOWN_SECONDS, CODE_VALIDITY.toMinutes());
        }
        accountTokenRepository.markUsed(account.getId(), List.of(EMAIL_CHANGE), now);
        String code = tokenService.newCode();
        accountTokenRepository.save(tokenMapper.toAccountToken(account, EMAIL_CHANGE, hash(account, target, code),
                now.plus(CODE_VALIDITY)));
        eventPublisher.publishEvent(new TwoFactorCodeRequested(target, fullName(account), code,
                "confirmar tu nuevo correo en Autopartco", CODE_VALIDITY.toMinutes()));
        auditService.record(account.getId(), "EMAIL_CHANGE_REQUESTED", "ACCOUNT", account.getId(), Map.of());
        return new EmailChangeRequested(true, COOLDOWN_SECONDS, CODE_VALIDITY.toMinutes());
    }

    /**
     * Confirma con el código y cambia el correo. El correo es el usuario de la sesión: se cierran todas las sesiones y la
     * persona vuelve a ingresar con el nuevo. Se avisa al correo anterior.
     */
    @Transactional(noRollbackFor = {BadRequestException.class, TooManyRequestsException.class})
    public void confirmEmailChange(String email, String newEmail, String code) {
        Account account = lock(email);
        String target = normalize(newEmail);
        List<AccountToken> active = accountTokenRepository.findActive(account.getId(), EMAIL_CHANGE, LocalDateTime.now());
        if (active.isEmpty()) {
            throw invalidCode();
        }
        AccountToken token = active.getFirst();
        String supplied = hash(account, target, code == null ? "" : code.trim());
        if (!MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), token.getToken().getBytes(StandardCharsets.UTF_8))) {
            short attempts = (short) (token.getAttempts() + 1);
            token.setAttempts(attempts);
            if (attempts >= MAX_ATTEMPTS) {
                token.setUsed(true);
                accountTokenRepository.save(token);
                throw new TooManyRequestsException("EMAIL_CHANGE_ATTEMPTS_EXCEEDED",
                        "Superaste el número de intentos. Solicita un nuevo código.");
            }
            accountTokenRepository.save(token);
            throw invalidCode();
        }
        token.setUsed(true);
        accountTokenRepository.save(token);
        requireAvailable(target);

        String previous = account.getEmail();
        account.setEmail(target);
        accountRepository.save(account);
        accountSessionService.revokeAll(account.getId());
        auditService.record(account.getId(), "EMAIL_CHANGED", "ACCOUNT", account.getId(), Map.of());
        securityNotice(account, previous, "Cambiaste tu correo",
                "El correo de tu cuenta de Autopartco ahora es " + mask(target) + ". Desde ahora ingresa con ese correo.");
    }

    // ------------------------------------------------------------------ ingreso social

    @Transactional(readOnly = true)
    public List<LinkedIdentity> identities(String email) {
        Account account = find(email);
        return jdbcTemplate.query("""
                SELECT provider, email, created_at FROM account_identities
                WHERE account_id = ? AND deleted_at IS NULL ORDER BY created_at
                """, (rs, row) -> new LinkedIdentity(rs.getString("provider"), rs.getString("email"),
                rs.getTimestamp("created_at").toLocalDateTime()), account.getId());
    }

    /** Desvincula un proveedor. Exige la contraseña: así la persona conserva un método de ingreso. */
    @Transactional
    public void unlink(String email, String provider, String currentPassword) {
        Account account = lock(email);
        try {
            requirePassword(account, currentPassword);
        } catch (BadRequestException wrong) {
            throw new ConflictException("LAST_SIGN_IN_METHOD",
                    "Para desvincular confirma tu contraseña. Si no la conoces, defínela con «¿Olvidaste tu contraseña?».");
        }
        List<AccountIdentity> linked = identityRepository.findByAccount_Id(account.getId()).stream()
                .filter(identity -> identity.getProvider().equalsIgnoreCase(provider))
                .toList();
        if (linked.isEmpty()) {
            throw new NotFoundException("IDENTITY_NOT_FOUND", "Ese método de ingreso no está vinculado.");
        }
        identityRepository.deleteAll(linked);
        auditService.record(account.getId(), "SOCIAL_UNLINKED", "ACCOUNT", account.getId(),
                Map.of("provider", provider.toLowerCase(Locale.ROOT)));
    }

    // ------------------------------------------------------------------ datos personales

    /** Copia de los datos de la cuenta (perfil, direcciones, pedidos, reseñas, preguntas y preferencias). */
    @Transactional(readOnly = true)
    public Map<String, Object> export(String email) {
        Account account = find(email);
        UUID personId = account.getPerson().getId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generatedAt", LocalDateTime.now().toString());
        data.put("account", jdbcTemplate.queryForMap("""
                SELECT a.email, t.code AS account_type, a.created_at, p.name, p.surname, p.identification,
                       p.date_birth, p.number_phone
                FROM accounts a JOIN persons p ON p.id = a.id_person JOIN types_accounts t ON t.id = a.type_id
                WHERE a.id = ?
                """, account.getId()));
        data.put("preferences", account.getConfiguration());
        data.put("addresses", jdbcTemplate.queryForList("""
                SELECT pa.label, pa.is_default, l.address_line, l.address_complement, l.city, l.department, l.postal_code
                FROM person_addresses pa JOIN locations l ON l.id = pa.location_id
                WHERE pa.person_id = ? AND pa.deleted_at IS NULL ORDER BY pa.created_at
                """, personId));
        data.put("orders", jdbcTemplate.queryForList("""
                SELECT o.order_number, st.code AS status, o.total, o.currency, o.created_at
                FROM orders o JOIN states st ON st.id = o.state_id
                WHERE o.person_id = ? AND o.deleted_at IS NULL ORDER BY o.created_at DESC
                """, personId));
        data.put("productReviews", jdbcTemplate.queryForList("""
                SELECT p.name AS product, r.rating, r.comment, r.created_at
                FROM product_reviews r JOIN products p ON p.id = r.product_id
                WHERE r.person_id = ? AND r.deleted_at IS NULL ORDER BY r.created_at DESC
                """, personId));
        data.put("storeReviews", jdbcTemplate.queryForList("""
                SELECT s.business_name AS store, r.rating, r.comment, r.created_at
                FROM store_reviews r JOIN store s ON s.id = r.store_id
                WHERE r.person_id = ? AND r.deleted_at IS NULL ORDER BY r.created_at DESC
                """, personId));
        data.put("questions", jdbcTemplate.queryForList("""
                SELECT p.name AS product, q.question, q.created_at
                FROM product_questions q JOIN products p ON p.id = q.product_id
                WHERE q.person_id = ? AND q.deleted_at IS NULL ORDER BY q.created_at DESC
                """, personId));
        data.put("linkedSignIns", identities(email));
        auditService.record(account.getId(), "PERSONAL_DATA_EXPORTED", "ACCOUNT", account.getId(), Map.of());
        return data;
    }

    // ------------------------------------------------------------------ eliminación

    @Transactional(readOnly = true)
    public DeletionRequest deletionRequest(String email) {
        Account account = find(email);
        return jdbcTemplate.query("""
                SELECT id, status, reason, created_at FROM account_deletion_requests
                WHERE account_id = ? ORDER BY created_at DESC LIMIT 1
                """, (rs, row) -> new DeletionRequest(rs.getObject("id", UUID.class), rs.getString("status"),
                rs.getString("reason"), rs.getTimestamp("created_at").toLocalDateTime()), account.getId())
                .stream().findFirst().orElse(null);
    }

    /**
     * Registra la solicitud (la ejecuta un administrador). Los dueños de un almacén activo deben transferirlo o cerrarlo
     * antes ({@code STORE_OWNER_CANNOT_DELETE}).
     */
    @Transactional
    public DeletionRequest requestDeletion(String email, String currentPassword, String reason) {
        Account account = lock(email);
        if (!identityRepository.existsByAccount_Id(account.getId()) || (currentPassword != null && !currentPassword.isBlank())) {
            requirePassword(account, currentPassword);
        }
        Boolean owner = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM collaborators c
                    JOIN roles_collaborators cr ON cr.collaborator_id = c.id
                    JOIN roles r ON r.id = cr.role_id
                    JOIN states s ON s.id = c.state
                    WHERE c.person_id = ? AND r.code = 'OWNER' AND s.code = 'ACTIVE' AND c.deleted_at IS NULL)
                """, Boolean.class, account.getPerson().getId());
        if (Boolean.TRUE.equals(owner)) {
            throw new ConflictException("STORE_OWNER_CANNOT_DELETE",
                    "Eres dueño de un almacén. Transfiérelo o ciérralo antes de eliminar tu cuenta.");
        }
        DeletionRequest current = deletionRequest(email);
        if (current != null && "PENDING".equals(current.status())) {
            throw new ConflictException("DELETION_ALREADY_REQUESTED", "Ya tienes una solicitud de eliminación en curso.");
        }
        String cleanReason = reason == null || reason.isBlank() ? null : reason.trim();
        jdbcTemplate.update("INSERT INTO account_deletion_requests (account_id, reason) VALUES (?, ?)",
                account.getId(), cleanReason);
        auditService.record(account.getId(), "ACCOUNT_DELETION_REQUESTED", "ACCOUNT", account.getId(), Map.of());
        return deletionRequest(email);
    }

    @Transactional
    public void cancelDeletion(String email) {
        Account account = lock(email);
        int updated = jdbcTemplate.update("""
                UPDATE account_deletion_requests SET status = 'CANCELLED', processed_at = now()
                WHERE account_id = ? AND status = 'PENDING'
                """, account.getId());
        if (updated == 0) {
            throw new NotFoundException("DELETION_REQUEST_NOT_FOUND", "No tienes una solicitud de eliminación en curso.");
        }
        auditService.record(account.getId(), "ACCOUNT_DELETION_CANCELLED", "ACCOUNT", account.getId(), Map.of());
    }

    // ------------------------------------------------------------------ apoyo

    private void requirePassword(Account account, String password) {
        if (password == null || password.isBlank() || !passwordEncoder.matches(password, account.getPassword())) {
            auditService.record(account.getId(), "PASSWORD_CHECK_FAILED", "ACCOUNT", account.getId(), Map.of());
            throw new BadRequestException("INVALID_PASSWORD", "La contraseña no es correcta.");
        }
    }

    private void requireAvailable(String target) {
        if (accountRepository.findByEmailIgnoreCase(target).isPresent()) {
            throw new ConflictException("EMAIL_ALREADY_EXISTS", "Ya existe una cuenta con ese correo.");
        }
    }

    private void securityNotice(Account account, String to, String title, String message) {
        eventPublisher.publishEvent(new SecurityNoticeRequested(to, fullName(account), title, message));
        notificationService.notify(account.getId(), NotificationKind.ACCOUNT_SECURITY, title, message, "/cuenta/seguridad");
    }

    private String hash(Account account, String target, String code) {
        return tokenService.hashVerificationCode(account.getId(), EMAIL_CHANGE + ":" + target + ":" + code);
    }

    private Account lock(String email) {
        return accountRepository.lockByEmail(normalize(email))
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "No se encontró la cuenta autenticada."));
    }

    private Account find(String email) {
        return accountRepository.findByEmailIgnoreCase(normalize(email))
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "No se encontró la cuenta autenticada."));
    }

    private static String fullName(Account account) {
        return account.getPerson().getName() + " " + account.getPerson().getSurname();
    }

    static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** {@code an***@correo.co}: no se escribe completo en el aviso al correo anterior. */
    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }

    private static BadRequestException invalidCode() {
        return new BadRequestException("INVALID_EMAIL_CODE", "El código no es válido o expiró.");
    }
}
