package com.autopartco.backend.core.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.identity.web.AddressLocationRequest;
import com.autopartco.backend.core.identity.web.PersonAddressRequest;
import com.autopartco.backend.store.entity.Location;
import com.autopartco.backend.store.repository.LocationRepository;

@ExtendWith(MockitoExtension.class)
class PersonAddressServiceTest {
    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PersonAddressRepository addressRepository;

    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private PersonAddressService personAddressService;

    @Test
    void makesFirstAddressDefaultAndAssociatesItWithAuthenticatedPerson() {
        Person person = new Person();
        person.setId(UUID.randomUUID());
        Account account = new Account();
        account.setPerson(person);
        when(accountRepository.findByEmailIgnoreCaseForUpdate("ana@example.com"))
                .thenReturn(Optional.of(account));
        when(addressRepository.findByPerson_IdOrderByIsDefaultDescCreatedAtDesc(person.getId()))
                .thenReturn(java.util.List.of());
        when(locationRepository.save(any(Location.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(addressRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = personAddressService.create(
                " Ana@Example.com ",
                new PersonAddressRequest(
                        "Casa",
                        new AddressLocationRequest(
                                "Calle 1 #2-3", null, "Bogotá", "Cundinamarca", null,
                                null, null, null
                        ),
                        false
                )
        );

        assertTrue(response.isDefault());
        assertEquals("Colombia", response.country());
    }
}
