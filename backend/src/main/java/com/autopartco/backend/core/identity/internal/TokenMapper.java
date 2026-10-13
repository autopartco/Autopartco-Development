package com.autopartco.backend.core.identity.internal;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;


@Component
public class TokenMapper {

    public AccountToken toAccountToken(
            Account account,
            String purpose,
            String token,
            LocalDateTime expirationAt
    ) {
        return AccountToken.builder()
                .account(account)
                .purpose(purpose)
                .token(token)
                .expirationAt(expirationAt)
                .used(false)
                .build();
    }
}
