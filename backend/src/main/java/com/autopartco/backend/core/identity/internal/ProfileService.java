package com.autopartco.backend.core.identity.internal;

import java.util.Locale;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.web.ProfileResponse;
import com.autopartco.backend.core.identity.web.ProfileUpdateRequest;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ProfileService {
    private final AccountRepository accountRepository;
    private final PersonRepository personRepository;

    @Transactional(readOnly = true)
    public ProfileResponse getOwnProfile(String authenticatedEmail) {
        return toResponse(findAccount(authenticatedEmail));
    }

    @Transactional
    public ProfileResponse updateOwnProfile(String authenticatedEmail, ProfileUpdateRequest request) {
        Account account = findAccount(authenticatedEmail);
        Person person = account.getPerson();

        if (request.identification() != null) {
            String identification = request.identification().trim();
            if (personRepository.existsByIdentificationAndIdNot(identification, person.getId())) {
                throw new ConflictException(
                        "IDENTIFICATION_ALREADY_EXISTS",
                        "Ya existe una persona con esta identificación."
                );
            }
            person.setIdentification(identification);
        }

        if (request.name() != null) {
            person.setName(request.name().trim());
        }
        if (request.surname() != null) {
            person.setSurname(request.surname().trim());
        }
        if (request.dateBirth() != null) {
            person.setDateBirth(LocalDate.parse(request.dateBirth()));
        }
        if (request.numberPhone() != null) {
            person.setNumberPhone(request.numberPhone().trim());
        }

        return toResponse(account);
    }

    private Account findAccount(String authenticatedEmail) {
        String email = authenticatedEmail.trim().toLowerCase(Locale.ROOT);
        return accountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "No se encontró la cuenta autenticada."));
    }

    private ProfileResponse toResponse(Account account) {
        Person person = account.getPerson();
        return new ProfileResponse(
                account.getId(),
                person.getId(),
                account.getEmail(),
                person.getName(),
                person.getSurname(),
                person.getIdentification(),
                person.getDateBirth().toString(),
                person.getNumberPhone()
        );
    }
}