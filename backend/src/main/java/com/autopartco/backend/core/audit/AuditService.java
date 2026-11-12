package com.autopartco.backend.core.audit;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.autopartco.backend.core.audit.internal.AuditLogMapper;
import com.autopartco.backend.core.audit.internal.AuditLogRepository;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
        log.debug("Recording account lock: accountId={}, failedAttempts={}", accountId, failedAttempts);
        repository.save(auditLogMapper.toAccountLocked(accountId, email, ipAddress, failedAttempts, state));
    }

    /**
     * Registro genérico de auditoría. {@code metadata} nunca debe llevar contraseñas, tokens ni datos de pago.
     *
     * @param accountId cuenta que actuó (o afectada si actúa el sistema); puede ser {@code null}
     */
    public void record(UUID accountId, String action, String entityType, UUID entityId, Map<String, Object> metadata) {
        repository.save(auditLogMapper.toEvent(accountId, action, entityType, entityId, metadata));
    }

    /** Si ya hay un registro con esa acción sobre esa entidad (marca idempotente de envíos y procesos únicos). */
    public boolean exists(String action, String entityType, UUID entityId) {
        return repository.existsByActionAndEntityTypeAndEntity(action, entityType, entityId);
    }

    public void recordAccountUnlocked(UUID accountId, String email, String state) {
        repository.save(auditLogMapper.toAccountUnlocked(accountId, email, state));
    }
}