package com.autopartco.backend.core.identity;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.internal.Account;
import com.autopartco.backend.core.identity.internal.AccountRepository;
import com.autopartco.backend.core.identity.internal.AccountType;
import com.autopartco.backend.core.identity.internal.AccountTypeRepository;
import com.autopartco.backend.core.identity.internal.Person;
import com.autopartco.backend.core.identity.internal.PersonRepository;
import com.autopartco.backend.core.identity.internal.RegistrationService;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.persistence.StateCodes;
import com.autopartco.backend.shared.persistence.StateRepository;

import lombok.AllArgsConstructor;

/**
 * Alta y cambios de tipo o estado de cuenta pedidos por otros módulos (registro de vendedor, alta de almacén,
 * aceptación de invitaciones de colaborador).
 */
@Service
@AllArgsConstructor
public class AccountProvisioning {
    private static final String STORE_MANAGER_TYPE = "STORE_MANAGER";

    private final RegistrationService registrationService;
    private final AccountRepository accountRepository;
    private final AccountTypeRepository accountTypeRepository;
    private final PersonRepository personRepository;
    private final StateRepository stateRepository;
    private final PasswordEncoder passwordEncoder;

    /** Crea persona y cuenta pendientes sin enviar el correo de verificación. */
    @Transactional
    public PreparedRegistration registerSellerAccount(RegisterRequest request) {
        return registrationService.registerSellerAccount(request);
    }

    public void sendSellerVerification(PreparedRegistration prepared) {
        registrationService.sendSellerVerification(prepared);
    }

    /** Cambia la cuenta al tipo {@code STORE_MANAGER} dentro de la transacción del llamador. */
    @Transactional
    public void promoteToStoreManager(UUID accountId) {
        AccountType storeManager = accountTypeRepository.findByCode(STORE_MANAGER_TYPE)
                .orElseThrow(() -> new NotFoundException(
                        "ACCOUNT_TYPE_NOT_FOUND",
                        "No existe el tipo de cuenta de vendedor."
                ));
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "No se encontró la cuenta."));
        account.setAccountType(storeManager);
    }

    /**
     * Crea la persona y una cuenta {@code STORE_MANAGER} ya activa para un colaborador que acepta una invitación
     * (el correo quedó verificado al recibir el enlace). La contraseña se guarda con el {@link PasswordEncoder}.
     *
     * <p><b>Uso restringido:</b> solo debe llamarlo {@code storesuite.staff.AcceptStaffInvitation}, dentro de su
     * transacción y después de bloquear la invitación pendiente y de {@link AccountLookup#lockByEmail(String)}
     * sin encontrar cuenta. No valida el token ni la invitación: llamarlo desde otro flujo crearía cuentas
     * activas sin verificar el correo.</p>
     *
     * @throws ConflictException {@code IDENTIFICATION_ALREADY_EXISTS} si la identificación ya está registrada
     */
    @Transactional
    public AccountRef createInvitedAccount(String email, InvitedAccountDetails details) {
        String identification = details.identification().trim();
        if (personRepository.existsByIdentification(identification)) {
            throw new ConflictException(
                    "IDENTIFICATION_ALREADY_EXISTS",
                    "Ya existe una persona registrada con esta identificación."
            );
        }
        AccountType accountType = accountTypeRepository.findByCode(STORE_MANAGER_TYPE)
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
                .state(findState(StateCodes.ACTIVE))
                .configuration(Map.of())
                .build();
        return AccountLookup.toRef(accountRepository.save(account));
    }

    /** Pasa la cuenta al estado {@code ACTIVE} dentro de la transacción del llamador. */
    @Transactional
    public void activate(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "No se encontró la cuenta."));
        account.setState(findState(StateCodes.ACTIVE));
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
}
