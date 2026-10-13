package com.autopartco.backend.core.audit.internal;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;


@Component
public class AuditLogMapper {

    public AuditLog toRegistrationRequested(UUID accountId, String email, String accountType, String state) {
        return AuditLog.builder()
                .accountId(accountId)
                .action("USER_REGISTRATION_REQUESTED")
                .entityType("ACCOUNT")
                .entity(accountId)
                .metadata(Map.of(
                        "email", email,
                        "accountType", accountType,
                        "state", state
                ))
                .build();
    }

    public AuditLog toEmailVerified(UUID accountId, String email, String state) {
        return AuditLog.builder()
                .accountId(accountId)
                .action("USER_EMAIL_VERIFIED")
                .entityType("ACCOUNT")
                .entity(accountId)
                .metadata(Map.of(
                        "email", email,
                        "state", state
                ))
                .build();
    }

    public AuditLog toAccountLocked(UUID accountId, String email, String ipAddress, int failedAttempts, String state) {
        System.out.print("AUDITANDO");
        return AuditLog.builder()
                .accountId(accountId)
                .action("ACCOUNT_LOCKED")
                .entityType("ACCOUNT")
                .entity(accountId)
                .metadata(Map.of(
                        "email", email,
                        "ipAddress", ipAddress,
                        "failedAttempts", failedAttempts,
                        "state", state
                ))
                .build();
    }

    public AuditLog toAccountUnlocked(UUID accountId, String email, String state) {
        return AuditLog.builder()
                .accountId(accountId)
                .action("ACCOUNT_UNLOCKED")
                .entityType("ACCOUNT")
                .entity(accountId)
                .metadata(Map.of(
                        "email", email,
                        "state", state
                ))
                .build();
    }
}