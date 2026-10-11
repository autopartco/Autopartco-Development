package com.autopartco.backend.auth.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.autopartco.backend.auth.dto.RegisterRequest;
import com.autopartco.backend.auth.dto.RegisterResponse;
import com.autopartco.backend.auth.entity.Account;
import com.autopartco.backend.auth.entity.AccountType;
import com.autopartco.backend.auth.entity.Person;
import com.autopartco.backend.auth.mapper.AccountMapper;
import com.autopartco.backend.auth.mapper.AuthResponseMapper;
import com.autopartco.backend.auth.repository.AccountRepository;
import com.autopartco.backend.auth.repository.PersonRepository;
import com.autopartco.backend.auth.repository.StateRepository;
import com.autopartco.backend.auth.repository.TypeAccountRepository;
import com.autopartco.backend.common.State;
import com.autopartco.backend.config.AppProperties;
import com.autopartco.backend.exception.ConflictException;
import com.autopartco.backend.exception.NotFoundException;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class RegistrationService {
    private static final String DEFAULT_ACCOUNT_TYPE = "CUSTOMER";
    private static final String REGISTRATION_STATE = "PENDING";

    private final AccountRepository accountRepository;
    private final PersonRepository personRepository;
    private final TypeAccountRepository typeAccountRepository;
    private final StateRepository stateRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountMapper accountMapper;
    private final AuthResponseMapper authResponseMapper;
    private final EmailVerificationTokenService tokenService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final AppProperties appProperties;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        PreparedRegistration prepared = prepare(request);
        sendVerification(prepared);
        return prepared.response();
    }

    @Transactional
    public PreparedRegistration registerSellerAccount(RegisterRequest request) {
        return prepare(request);
    }

    public void sendSellerVerification(PreparedRegistration prepared) {
        sendVerification(prepared);
    }

    private PreparedRegistration prepare(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String identification = request.identification().trim();

        if (accountRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_ALREADY_EXISTS", "Ya existe una cuenta con este correo.");
        }

        if (personRepository.existsByIdentification(identification)) {
            throw new ConflictException("IDENTIFICATION_ALREADY_EXISTS", "Ya existe una persona con esta identificación.");
        }

        AccountType accountType = typeAccountRepository.findByCode(DEFAULT_ACCOUNT_TYPE)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_TYPE_NOT_FOUND", "No existe el tipo de cuenta por defecto."));

        State state = stateRepository.findByCode(REGISTRATION_STATE)
                .orElseThrow(() -> new NotFoundException("STATE_NOT_FOUND", "No existe el estado inicial del registro."));

        Person person = accountMapper.toPerson(request, identification);
        Account account = accountMapper.toPendingAccount(
                request,
                email,
                passwordEncoder.encode(request.password()),
                person,
                accountType,
                state
        );

        personRepository.save(person);
        Account savedAccount = accountRepository.save(account);
        EmailVerificationTokenService.CreatedEmailVerificationToken token = tokenService.create(savedAccount);

        auditService.recordRegistrationRequested(savedAccount);
        return new PreparedRegistration(
                authResponseMapper.toRegisterResponse(savedAccount, token.expiresAt()),
                person.getName() + " " + person.getSurname(),
                buildVerificationLink(token.rawToken())
        );
    }

    private void sendVerification(PreparedRegistration prepared) {
        emailService.sendEmailVerification(
                prepared.response().email(),
                prepared.fullName(),
                prepared.verificationLink()
        );
    }

    private String buildVerificationLink(String rawToken) {
        return UriComponentsBuilder
                .fromUriString(appProperties.frontendBaseUrl().replaceAll("/+$", ""))
                .path("/verify-email")
                .queryParam("token", rawToken)
                .build()
                .toUriString();
    }

    public record PreparedRegistration(
            RegisterResponse response,
            String fullName,
            String verificationLink
    ) {
    }
}
