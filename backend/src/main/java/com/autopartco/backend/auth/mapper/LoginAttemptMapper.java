package com.autopartco.backend.auth.mapper;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.autopartco.backend.auth.entity.Account;
import com.autopartco.backend.auth.entity.LoginAttempt;

@Component
public class LoginAttemptMapper {

    public LoginAttempt toLoginAttempt(Account account, boolean successful, String ipAddress) {
        System.out.print("SUCCESS: " + successful);
        return LoginAttempt.builder()
                .account(account)
                .attemptedAt(LocalDateTime.now())
                .successful(successful)
                .ipAddress(ipAddress)
                .build();
    }
}
