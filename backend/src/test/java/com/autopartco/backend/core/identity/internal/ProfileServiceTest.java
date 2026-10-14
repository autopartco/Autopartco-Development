package com.autopartco.backend.core.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.identity.web.ProfileResponse;
import com.autopartco.backend.core.identity.web.ProfileUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {
    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PersonRepository personRepository;

    @InjectMocks
    private ProfileService profileService;

    @Test
    void loadsOnlyTheProfileAssociatedWithTheAuthenticatedEmail() {
        Account account = account("ana@example.com", "hash-secreto");
        when(accountRepository.findByEmailIgnoreCase("ana@example.com"))
                .thenReturn(Optional.of(account));

        ProfileResponse response = profileService.getOwnProfile(" Ana@Example.com ");

        assertEquals(account.getId(), response.accountId());
        assertEquals(account.getPerson().getId(), response.personId());
        assertEquals("ana@example.com", response.email());
        assertEquals("Ana", response.name());
        assertFalse(Arrays.stream(ProfileResponse.class.getRecordComponents())
                .anyMatch(component -> component.getName().equalsIgnoreCase("password")));
        verify(accountRepository).findByEmailIgnoreCase("ana@example.com");
    }

    @Test
    void updatesAllowedFieldsWithoutChangingAccountCredentials() {
        Account account = account("ana@example.com", "hash-secreto");
        when(accountRepository.findByEmailIgnoreCase("ana@example.com"))
                .thenReturn(Optional.of(account));
        when(personRepository.existsByIdentificationAndIdNot("987654", account.getPerson().getId()))
                .thenReturn(false);

        ProfileResponse response = profileService.updateOwnProfile(
                "ana@example.com",
                new ProfileUpdateRequest("Ana María", null, "987654", "1995-04-20", null)
        );

        assertEquals("Ana María", response.name());
        assertEquals("987654", response.identification());
        assertEquals("1995-04-20", response.dateBirth());
        assertEquals("ana@example.com", account.getEmail());
        assertEquals("hash-secreto", account.getPassword());
        verify(personRepository).existsByIdentificationAndIdNot("987654", account.getPerson().getId());
    }

    @Test
    void rejectsFieldsOutsideTheProfileUpdateContract() {
        ObjectMapper objectMapper = new ObjectMapper();

        assertThrows(UnrecognizedPropertyException.class, () -> objectMapper.readValue(
                "{\"name\":\"Luis Antonio\",\"surname\":\"Torres Perez\","
                        + "\"numberPhone\":\"3117093571\",\"dateBirth\":\"2007-09-25\","
                        + "\"password\":\"\"}",
                ProfileUpdateRequest.class
        ));
    }

    private Account account(String email, String password) {
        Person person = new Person();
        person.setId(UUID.randomUUID());
        person.setName("Ana");
        person.setSurname("Pérez");
        person.setIdentification("123456");
        person.setDateBirth(LocalDate.of(1995, 4, 20));
        person.setNumberPhone("3001234567");

        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setPerson(person);
        account.setEmail(email);
        account.setPassword(password);
        return account;
    }
}