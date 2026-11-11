package com.autopartco.backend.core.identity.internal.social;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.identity.LoginResponse;
import com.autopartco.backend.core.identity.internal.Account;
import com.autopartco.backend.core.identity.internal.AccountRepository;
import com.autopartco.backend.core.identity.internal.AccountStatusService;
import com.autopartco.backend.core.identity.internal.AccountType;
import com.autopartco.backend.core.identity.internal.AccountTypeRepository;
import com.autopartco.backend.core.identity.internal.LoginAttemptService;
import com.autopartco.backend.core.identity.internal.Person;
import com.autopartco.backend.core.identity.internal.PersonRepository;
import com.autopartco.backend.core.identity.internal.SessionOpener;
import com.autopartco.backend.core.identity.internal.TwoFactorLoginService;
import com.autopartco.backend.core.identity.web.SocialLoginRequest;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.error.UnauthorizedException;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.persistence.StateCodes;
import com.autopartco.backend.shared.persistence.StateRepository;

/**
 * Ingreso con Google o Facebook. El token se valida en el servidor; después:
 * <ol>
 *   <li>Identidad ya vinculada: abre sesión.</li>
 *   <li>Correo de una cuenta existente: Google con correo verificado la vincula; Facebook responde 409
 *       {@code SOCIAL_LINK_REQUIRED} (Meta no garantiza el correo, así se evita tomar cuentas ajenas).</li>
 *   <li>Sin cuenta: 409 {@code SOCIAL_REGISTRATION_REQUIRED} con los datos del proveedor; con {@code registration}
 *       crea una cuenta CUSTOMER activa (el proveedor ya probó el correo) con contraseña aleatoria inutilizable.</li>
 * </ol>
 */
@Service
public class SocialLoginService {
    static final String CUSTOMER = "CUSTOMER";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<SocialProvider, SocialTokenVerifier> verifiers;
    private final AccountIdentityRepository identityRepository;
    private final AccountRepository accountRepository;
    private final PersonRepository personRepository;
    private final AccountTypeRepository accountTypeRepository;
    private final StateRepository stateRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountStatusService accountStatusService;
    private final LoginAttemptService loginAttemptService;
    private final SessionOpener sessionOpener;
    private final TwoFactorLoginService twoFactorLoginService;
    private final AuditService auditService;

    public SocialLoginService(
            List<SocialTokenVerifier> verifiers,
            AccountIdentityRepository identityRepository,
            AccountRepository accountRepository,
            PersonRepository personRepository,
            AccountTypeRepository accountTypeRepository,
            StateRepository stateRepository,
            PasswordEncoder passwordEncoder,
            AccountStatusService accountStatusService,
            LoginAttemptService loginAttemptService,
            SessionOpener sessionOpener,
            TwoFactorLoginService twoFactorLoginService,
            AuditService auditService
    ) {
        this.verifiers = new java.util.EnumMap<>(SocialProvider.class);
        verifiers.forEach(verifier -> this.verifiers.put(verifier.provider(), verifier));
        this.identityRepository = identityRepository;
        this.accountRepository = accountRepository;
        this.personRepository = personRepository;
        this.accountTypeRepository = accountTypeRepository;
        this.stateRepository = stateRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountStatusService = accountStatusService;
        this.loginAttemptService = loginAttemptService;
        this.sessionOpener = sessionOpener;
        this.twoFactorLoginService = twoFactorLoginService;
        this.auditService = auditService;
    }

    @Transactional
    public LoginResponse login(SocialProvider provider, SocialLoginRequest request, String ipAddress, String userAgent) {
        SocialTokenVerifier verifier = verifiers.get(provider);
        if (verifier == null || !verifier.enabled()) {
            throw new NotFoundException("SOCIAL_PROVIDER_DISABLED", "Este medio de ingreso no está disponible.");
        }
        SocialIdentity identity = verifier.verify(request.token());
        if (identity.subject() == null || identity.subject().isBlank()) {
            throw SocialTokenVerifier.invalidToken();
        }

        Optional<AccountIdentity> linked = identityRepository.findByProviderAndSubject(provider.code(), identity.subject());
        if (linked.isPresent()) {
            Account account = linked.get().getAccount();
            return openSession(account, provider, ipAddress, userAgent, "SOCIAL_LOGIN");
        }

        String email = normalize(identity.email());
        if (email == null) {
            throw new BadRequestException("SOCIAL_EMAIL_REQUIRED",
                    "Tu cuenta del proveedor no comparte un correo. Autoriza el correo o regístrate con tu correo y contraseña.");
        }

        Optional<Account> existing = accountRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            if (provider != SocialProvider.GOOGLE || !identity.emailVerified()) {
                throw new ConflictException("SOCIAL_LINK_REQUIRED",
                        "Ya existe una cuenta con este correo. Ingresa con tu contraseña para continuar.",
                        Map.of("email", email));
            }
            Account account = existing.get();
            ensureCanSignIn(account);
            link(account, identity, email);
            return openSession(account, provider, ipAddress, userAgent, "SOCIAL_LINKED");
        }

        SocialLoginRequest.Registration registration = request.registration();
        if (registration == null) {
            Map<String, Object> prefill = new LinkedHashMap<>();
            prefill.put("email", email);
            prefill.put("name", identity.givenName());
            prefill.put("surname", identity.familyName());
            throw new ConflictException("SOCIAL_REGISTRATION_REQUIRED",
                    "Completa tus datos para crear tu cuenta.", prefill);
        }
        Account account = createAccount(identity, email, registration);
        link(account, identity, email);
        auditService.record(account.getId(), "USER_REGISTERED_SOCIAL", "ACCOUNT", account.getId(),
                Map.of("provider", provider.code(), "email", email));
        return openSession(account, provider, ipAddress, userAgent, "SOCIAL_LOGIN");
    }

    private LoginResponse openSession(Account account, SocialProvider provider, String ipAddress, String userAgent, String action) {
        ensureCanSignIn(account);
        if (twoFactorLoginService.isEnabled(account)) {
            // Mismo desafío que el login con contraseña: la sesión se abre en POST /auth/login/two-factor.
            return twoFactorLoginService.startChallenge(account);
        }
        loginAttemptService.record(account, true, ipAddress);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", provider.code());
        if (ipAddress != null) {
            metadata.put("ipAddress", ipAddress);
        }
        auditService.record(account.getId(), action, "ACCOUNT", account.getId(), metadata);
        return sessionOpener.open(account, ipAddress, userAgent);
    }

    /** Mismas reglas que el login con contraseña: solo cuentas activas y no bloqueadas. */
    private void ensureCanSignIn(Account account) {
        boolean active = account.getState() != null && StateCodes.ACTIVE.equals(account.getState().getCode());
        if (!active || accountStatusService.isLocked(account)) {
            throw new UnauthorizedException("UNAUTHORIZED", "Tu cuenta no puede ingresar en este momento.");
        }
    }

    private void link(Account account, SocialIdentity identity, String email) {
        identityRepository.save(AccountIdentity.builder()
                .account(account)
                .provider(identity.provider().code())
                .subject(identity.subject())
                .email(email)
                .build());
    }

    private Account createAccount(SocialIdentity identity, String email, SocialLoginRequest.Registration registration) {
        String identification = registration.identification().trim();
        if (personRepository.existsByIdentification(identification)) {
            throw new ConflictException("IDENTIFICATION_ALREADY_EXISTS", "Ya existe una persona con esta identificación.");
        }
        String name = firstPresent(registration.name(), identity.givenName());
        String surname = firstPresent(registration.surname(), identity.familyName());
        if (name == null || surname == null) {
            throw new BadRequestException("SOCIAL_NAME_REQUIRED", "Escribe tu nombre y apellido.");
        }
        AccountType type = accountTypeRepository.findByCode(CUSTOMER)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_TYPE_NOT_FOUND", "No existe el tipo de cuenta por defecto."));
        State active = stateRepository.findByCode(StateCodes.ACTIVE)
                .orElseThrow(() -> new NotFoundException("STATE_NOT_FOUND", "No existe el estado activo."));
        Person person = personRepository.save(Person.builder()
                .name(name)
                .surname(surname)
                .identification(identification)
                .dateBirth(registration.dateBirth())
                .numberPhone(registration.numberPhone().trim())
                .build());
        return accountRepository.save(Account.builder()
                .person(person)
                .email(email)
                .password(passwordEncoder.encode(randomSecret()))
                .accountType(type)
                .state(active)
                .configuration(Map.of())
                .build());
    }

    private static String normalize(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstPresent(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        return fallback == null || fallback.isBlank() ? null : fallback.trim();
    }

    /** Contraseña que nadie conoce: la persona puede definir una con "olvidé mi contraseña". */
    private static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
