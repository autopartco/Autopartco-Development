package com.autopartco.backend.core.identity.internal;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;


@Component
public class LoginAttemptMapper {

    public LoginAttempt toLoginAttempt(Account account, boolean successful, String ipAddress) {
        return LoginAttempt.builder()
                .account(account)
                .attemptedAt(LocalDateTime.now())
                .successful(successful)
                .ipAddress(ipAddress)
                .build();
    }
}
