package com.autopartco.backend.core.identity;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.identity.internal.Account;
import com.autopartco.backend.core.identity.internal.AccountRepository;
import com.autopartco.backend.core.identity.internal.AccountSessionService;
import com.autopartco.backend.core.identity.internal.AccountType;
import com.autopartco.backend.core.identity.internal.AccountTypeRepository;
import com.autopartco.backend.core.identity.internal.PasswordRecoveryService;
import com.autopartco.backend.core.identity.internal.Person;
import com.autopartco.backend.core.identity.internal.PersonRepository;
import com.autopartco.backend.core.identity.web.PasswordRecoveryRequest;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.persistence.StateCodes;
import com.autopartco.backend.shared.persistence.StateRepository;

import lombok.AllArgsConstructor;

/**
 * Acciones de la consola de administración sobre cuentas: alta de cuentas del equipo ({@code STAFF}), bloqueo y
 * activación, y cierre de sesiones. No comprueba permisos: lo hacen los casos de uso de {@code operations}. Cada
 * acción queda en la auditoría con quien la hizo.
 */
@Service
@AllArgsConstructor
public class AccountAdministration {
    static final String STAFF_TYPE = "STAFF";

    private final AccountRepository accountRepository;
    private final AccountTypeRepository accountTypeRepository;
    private final PersonRepository personRepository;
    private final StateRepository stateRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountSessionService sessionService;
    private final PasswordRecoveryService passwordRecoveryService;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Crea una cuenta {@code STAFF} activa con una contraseña aleatoria que nadie conoce y envía el correo de
     * recuperación para que la persona defina la suya.
     *
     * @throws ConflictException {@code ACCOUNT_ALREADY_EXISTS} o {@code IDENTIFICATION_ALREADY_EXISTS}
     */
    @Transactional
    public AccountRef createStaffAccount(String email, StaffAccountDetails details, UUID actorAccountId) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (accountRepository.findByEmailIgnoreCase(normalized).isPresent()) {
            throw new ConflictException("ACCOUNT_ALREADY_EXISTS", "Ya existe una cuenta con este correo.");
        }
        String identification = details.identification().trim();
        if (personRepository.existsByIdentification(identification)) {
            throw new ConflictException("IDENTIFICATION_ALREADY_EXISTS",
                    "Ya existe una persona registrada con esta identificación.");
        }
        AccountType type = accountTypeRepository.findByCode(STAFF_TYPE)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_TYPE_NOT_FOUND",
                        "No existe el tipo de cuenta del equipo de la plataforma."));
        Person person = personRepository.save(Person.builder()
                .name(details.name().trim())
                .surname(details.surname().trim())
                .identification(identification)
                .dateBirth(details.dateBirth())
                .numberPhone(details.numberPhone().trim())
                .build());
        Account account = accountRepository.save(Account.builder()
                .person(person)
                .email(normalized)
                .password(passwordEncoder.encode(randomSecret()))
                .accountType(type)
                .state(state(StateCodes.ACTIVE))
                .configuration(Map.of())
                .build());
        auditService.record(actorAccountId, "STAFF_ACCOUNT_CREATED", "ACCOUNT", account.getId(),
                Map.of("email", normalized));
        passwordRecoveryService.requestRecovery(new PasswordRecoveryRequest(normalized));
        return AccountLookup.toRef(account);
    }

    /** {@code ACTIVE} o {@code INACTIVE}; al desactivar se cierran todas sus sesiones. Activar también desbloquea. */
    @Transactional
    public AccountRef setActive(UUID accountId, boolean active, UUID actorAccountId) {
        Account account = require(accountId);
        account.setState(state(active ? StateCodes.ACTIVE : StateCodes.INACTIVE));
        if (!active) {
            sessionService.revokeAll(accountId);
        }
        auditService.record(actorAccountId, active ? "ADMIN_ACCOUNT_ACTIVATED" : "ADMIN_ACCOUNT_DEACTIVATED",
                "ACCOUNT", accountId, Map.of("email", account.getEmail()));
        return AccountLookup.toRef(accountRepository.save(account));
    }

    /** Cierra todas las sesiones de la cuenta (sus tokens de renovación dejan de servir). */
    @Transactional
    public void revokeSessions(UUID accountId, UUID actorAccountId) {
        Account account = require(accountId);
        sessionService.revokeAll(accountId);
        auditService.record(actorAccountId, "ADMIN_SESSIONS_REVOKED", "ACCOUNT", accountId,
                Map.of("email", account.getEmail()));
    }

    private Account require(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "No se encontró la cuenta."));
    }

    private State state(String code) {
        return stateRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("STATE_NOT_FOUND", "No está configurado el estado " + code + "."));
    }

    private String randomSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
