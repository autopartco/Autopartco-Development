package com.autopartco.backend.auth.service;

import org.springframework.stereotype.Service;

import com.autopartco.backend.auth.entity.Account;
import com.autopartco.backend.auth.repository.AuditLogsRepository;
import com.autopartco.backend.auth.mapper.AuditLogMapper;
import com.autopartco.backend.common.State;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class AuditService {
    private final AuditLogsRepository repository;
    private final AuditLogMapper auditLogMapper;

    public void recordRegistrationRequested(Account account) {
        repository.save(auditLogMapper.toRegistrationRequested(account));
    }

    public void recordEmailVerified(Account account, State state) {
        repository.save(auditLogMapper.toEmailVerified(account, state));
    }

    public void recordAccountLocked(Account account, String ipAddress, int failedAttempts) {
        System.out.print("BLOQUEANDO");
        repository.save(auditLogMapper.toAccountLocked(account, ipAddress, failedAttempts));
    }

    public void recordAccountUnlocked(Account account) {
        repository.save(auditLogMapper.toAccountUnlocked(account));
    }
}
