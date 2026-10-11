package com.autopartco.backend.auth.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.auth.dto.AddressLocationRequest;
import com.autopartco.backend.auth.dto.PersonAddressRequest;
import com.autopartco.backend.auth.dto.PersonAddressResponse;
import com.autopartco.backend.auth.entity.Account;
import com.autopartco.backend.auth.entity.PersonAddress;
import com.autopartco.backend.auth.repository.AccountRepository;
import com.autopartco.backend.auth.repository.PersonAddressRepository;
import com.autopartco.backend.exception.NotFoundException;
import com.autopartco.backend.store.entity.Location;
import com.autopartco.backend.store.repository.LocationRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class PersonAddressService {
    private final AccountRepository accountRepository;
    private final PersonAddressRepository addressRepository;
    private final LocationRepository locationRepository;

    @Transactional(readOnly = true)
    public List<PersonAddressResponse> list(String email) {
        Account account = findAccount(email);
        return addressRepository.findByPerson_IdOrderByIsDefaultDescCreatedAtDesc(account.getPerson().getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PersonAddressResponse create(String email, PersonAddressRequest request) {
        Account account = lockAccount(email);
        UUID personId = account.getPerson().getId();
        boolean makeDefault = request.isDefault()
                || addressRepository.findByPerson_IdOrderByIsDefaultDescCreatedAtDesc(personId).isEmpty();
        if (makeDefault) {
            addressRepository.clearDefaultForPerson(personId);
        }

        PersonAddress address = PersonAddress.builder()
                .person(account.getPerson())
                .location(locationRepository.save(toLocation(request.location())))
                .label(request.label().trim())
                .isDefault(makeDefault)
                .build();
        return toResponse(addressRepository.save(address));
    }

    @Transactional
    public PersonAddressResponse update(String email, UUID addressId, PersonAddressRequest request) {
        Account account = lockAccount(email);
        PersonAddress address = findOwnedAddress(addressId, account.getPerson().getId());
        boolean wasDefault = address.isDefault();
        boolean makeDefault = request.isDefault();
        if (request.isDefault()) {
            addressRepository.clearDefaultForPerson(account.getPerson().getId());
        } else if (wasDefault) {
            addressRepository.clearDefaultForPerson(account.getPerson().getId());
            var nextDefault = addressRepository.findFirstByPerson_IdAndIdNotOrderByCreatedAtDesc(
                    account.getPerson().getId(),
                    addressId
            );
            if (nextDefault.isPresent()) {
                nextDefault.get().setDefault(true);
            } else {
                makeDefault = true;
            }
        }

        address.setLabel(request.label().trim());
        address.setLocation(locationRepository.save(toLocation(request.location())));
        address.setDefault(makeDefault);
        return toResponse(address);
    }

    @Transactional
    public void delete(String email, UUID addressId) {
        Account account = lockAccount(email);
        UUID personId = account.getPerson().getId();
        PersonAddress address = findOwnedAddress(addressId, personId);
        boolean wasDefault = address.isDefault();
        addressRepository.delete(address);

        if (wasDefault) {
            addressRepository.clearDefaultForPerson(personId);
            addressRepository.findFirstByPerson_IdOrderByCreatedAtDesc(personId)
                    .ifPresent(nextDefault -> nextDefault.setDefault(true));
        }
    }

    private Account findAccount(String email) {
        return accountRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new NotFoundException(
                        "ACCOUNT_NOT_FOUND",
                        "No se encontró la cuenta autenticada."
                ));
    }

    private Account lockAccount(String email) {
        return accountRepository.findByEmailIgnoreCaseForUpdate(normalizeEmail(email))
                .orElseThrow(() -> new NotFoundException(
                        "ACCOUNT_NOT_FOUND",
                        "No se encontró la cuenta autenticada."
                ));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private PersonAddress findOwnedAddress(UUID addressId, UUID personId) {
        return addressRepository.findByIdAndPerson_Id(addressId, personId)
                .orElseThrow(() -> new NotFoundException(
                        "ADDRESS_NOT_FOUND",
                        "No se encontró la dirección."
                ));
    }

    private Location toLocation(AddressLocationRequest request) {
        return Location.builder()
                .addressLine(request.addressLine().trim())
                .addressComplement(clean(request.addressComplement()))
                .city(request.city().trim())
                .department(request.department().trim())
                .country(request.country() == null || request.country().isBlank()
                        ? "Colombia"
                        : request.country().trim())
                .postalCode(clean(request.postalCode()))
                .latitude(request.latitude())
                .longitude(request.longitude())
                .provider("INTERNAL")
                .metadata(Map.of())
                .build();
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private PersonAddressResponse toResponse(PersonAddress address) {
        Location location = address.getLocation();
        return new PersonAddressResponse(
                address.getId(),
                address.getLabel(),
                address.isDefault(),
                location.getAddressLine(),
                location.getAddressComplement(),
                location.getCity(),
                location.getDepartment(),
                location.getCountry(),
                location.getPostalCode(),
                location.getLatitude(),
                location.getLongitude()
        );
    }
}
