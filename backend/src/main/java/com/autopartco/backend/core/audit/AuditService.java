package com.autopartco.backend.core.audit;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.autopartco.backend.core.audit.internal.AuditLogMapper;
import com.autopartco.backend.core.audit.internal.AuditLogRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class AuditService {
    private final AuditLogRepository repository;
    private final AuditLogMapper auditLogMapper;

    public void recordRegistrationRequested(UUID accountId, String email, String accountType, String state) {
        repository.save(auditLogMapper.toRegistrationRequested(accountId, email, accountType, state));
    }

    public void recordEmailVerified(UUID accountId, String email, String state) {
        repository.save(auditLogMapper.toEmailVerified(accountId, email, state));
    }

    public void recordAccountLocked(UUID accountId, String email, String ipAddress, int failedAttempts, String state) {
        System.out.print("BLOQUEANDO");
        repository.save(auditLogMapper.toAccountLocked(accountId, email, ipAddress, failedAttempts, state));
    }

    public void recordAccountUnlocked(UUID accountId, String email, String state) {
        repository.save(auditLogMapper.toAccountUnlocked(accountId, email, state));
    }
}