package com.autopartco.backend.core.identity.internal;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.autopartco.backend.core.identity.RegisterRequest;
import com.autopartco.backend.shared.persistence.State;

@Component
public class AccountMapper {

    public Person toPerson(RegisterRequest request, String identification) {
        return Person.builder()
                .name(request.name().trim())
                .surname(request.surname().trim())
                .identification(identification)
                .dateBirth(request.dateBirth())
                .numberPhone(request.numberPhone().trim())
                .build();
    }

    public Account toPendingAccount(RegisterRequest request, String email, String encodedPassword, Person person, AccountType accountType, State state) {
        return Account.builder()
                .person(person)
                .email(email)
                .password(encodedPassword)
                .accountType(accountType)
                .state(state)
                .configuration(Map.of())
                .build();
    }
}
