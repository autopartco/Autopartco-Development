package com.autopartco.backend.operations.auditlog;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;

import lombok.AllArgsConstructor;

/** Registra en auditoría una acción de administración con la cuenta root que la hizo como actor. */
@Component
@AllArgsConstructor
public class AdminAudit {
    private final AuditService auditService;
    private final AccountLookup accountLookup;

    public void record(Authentication authentication, String action, String entityType, UUID entityId, Map<String, Object> metadata) {
        String email = authentication == null ? null : authentication.getName();
        UUID actorId = email == null ? null : accountLookup.findByEmail(email).map(AccountRef::accountId).orElse(null);
        Map<String, Object> data = new LinkedHashMap<>();
        if (metadata != null) {
            metadata.forEach((key, value) -> {
                if (value != null) {
                    data.put(key, value);
                }
            });
        }
        auditService.record(actorId, action, entityType, entityId, data);
    }
}
