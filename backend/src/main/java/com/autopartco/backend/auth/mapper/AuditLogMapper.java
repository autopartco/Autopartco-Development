package com.autopartco.backend.auth.mapper;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.autopartco.backend.auth.entity.Account;
import com.autopartco.backend.common.AuditLogs;
import com.autopartco.backend.shared.persistence.State;

@Component
public class AuditLogMapper {

    public AuditLogs toRegistrationRequested(Account account) {
        return AuditLogs.builder()
                .account(account)
                .action("USER_REGISTRATION_REQUESTED")
                .entityType("ACCOUNT")
                .entity(account.getId())
                .metadata(Map.of(
                        "email", account.getEmail(),
                        "accountType", account.getAccountType().getCode(),
                        "state", account.getState().getCode()
                ))
                .build();
    }

    public AuditLogs toEmailVerified(Account account, State state) {
        return AuditLogs.builder()
                .account(account)
                .action("USER_EMAIL_VERIFIED")
                .entityType("ACCOUNT")
                .entity(account.getId())
                .metadata(Map.of(
                        "email", account.getEmail(),
                        "state", state.getCode()
                ))
                .build();
    }

    public AuditLogs toAccountLocked(Account account, String ipAddress, int failedAttempts) {
        System.out.print("AUDITANDO");
        return AuditLogs.builder()
                .account(account)
                .action("ACCOUNT_LOCKED")
                .entityType("ACCOUNT")
                .entity(account.getId())
                .metadata(Map.of(
                        "email", account.getEmail(),
                        "ipAddress", ipAddress,
                        "failedAttempts", failedAttempts,
                        "state", account.getState().getCode()
                ))
                .build();
    }

    public AuditLogs toAccountUnlocked(Account account) {
        return AuditLogs.builder()
                .account(account)
                .action("ACCOUNT_UNLOCKED")
                .entityType("ACCOUNT")
                .entity(account.getId())
                .metadata(Map.of(
                        "email", account.getEmail(),
                        "state", account.getState().getCode()
                ))
                .build();
    }
}
