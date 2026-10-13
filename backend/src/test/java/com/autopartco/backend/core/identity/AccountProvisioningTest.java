package com.autopartco.backend.core.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.autopartco.backend.core.identity.internal.Account;
import com.autopartco.backend.core.identity.internal.AccountRepository;
import com.autopartco.backend.core.identity.internal.AccountType;
import com.autopartco.backend.core.identity.internal.AccountTypeRepository;
import com.autopartco.backend.core.identity.internal.Person;
import com.autopartco.backend.core.identity.internal.PersonRepository;
import com.autopartco.backend.core.identity.internal.RegistrationService;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.persistence.StateRepository;

@ExtendWith(MockitoExtension.class)
class AccountProvisioningTest {
    @Mock
    private RegistrationService registrationService;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountTypeRepository accountTypeRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private StateRepository stateRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountProvisioning accountProvisioning;

    private final InvitedAccountDetails details = new InvitedAccountDetails(
            " Ana ",
            " Pérez ",
            " 123456 ",
            LocalDate.of(1995, 4, 20),
            " 3001234567 ",
            "Autopartco#2026"
    );

    @Test
    void createsAnActiveStoreManagerAccountWithEncodedPasswordAndNormalizedEmail() {
        AccountType storeManager = AccountType.builder().code("STORE_MANAGER").build();
        State active = State.builder().code("ACTIVE").build();
        when(personRepository.existsByIdentification("123456")).thenReturn(false);
        when(accountTypeRepository.findByCode("STORE_MANAGER")).thenReturn(Optional.of(storeManager));
        when(personRepository.save(any(Person.class))).thenAnswer(invocation -> {
            Person person = invocation.getArgument(0);
            person.setId(UUID.randomUUID());
            return person;
        });
        when(passwordEncoder.encode("Autopartco#2026")).thenReturn("{bcrypt}hash");
        when(stateRepository.findByCode("ACTIVE")).thenReturn(Optional.of(active));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountRef created = accountProvisioning.createInvitedAccount(" Nuevo@Example.COM ", details);

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertEquals("nuevo@example.com", saved.getValue().getEmail());
        assertEquals("{bcrypt}hash", saved.getValue().getPassword());
        assertEquals("STORE_MANAGER", saved.getValue().getAccountType().getCode());
        assertEquals("ACTIVE", saved.getValue().getState().getCode());
        assertEquals("Ana", saved.getValue().getPerson().getName());
        assertEquals("3001234567", saved.getValue().getPerson().getNumberPhone());
        assertEquals("nuevo@example.com", created.email());
        assertEquals("ACTIVE", created.stateCode());
    }

    @Test
    void rejectsADuplicateIdentificationBeforeCreatingAnything() {
        when(personRepository.existsByIdentification("123456")).thenReturn(true);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> accountProvisioning.createInvitedAccount("nuevo@example.com", details)
        );

        assertEquals("IDENTIFICATION_ALREADY_EXISTS", exception.getCode());
        verify(personRepository, never()).save(any());
        verifyNoInteractions(accountRepository, passwordEncoder, accountTypeRepository, stateRepository);
    }
}
