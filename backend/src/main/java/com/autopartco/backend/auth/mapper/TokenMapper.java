package com.autopartco.backend.auth.mapper;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.autopartco.backend.auth.entity.Account;
import com.autopartco.backend.auth.entity.AccountToken;

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
