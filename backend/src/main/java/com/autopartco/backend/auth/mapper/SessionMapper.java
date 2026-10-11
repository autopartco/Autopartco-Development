package com.autopartco.backend.auth.mapper;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.autopartco.backend.auth.entity.Account;
import com.autopartco.backend.auth.entity.AccountSession;

@Component
public class SessionMapper {

    public AccountSession toAccountSession(
            UUID sessionId,
            Account account,
            String refreshTokenHash,
            String ipAddress,
            String userAgent,
            LocalDateTime expiredAt
    ) {
        return AccountSession.builder()
                .id(sessionId)
                .account(account)
                .refreshTokenHash(refreshTokenHash)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .expiredAt(expiredAt)
                .build();
    }
}
