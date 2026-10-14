package com.autopartco.backend.core.audit.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.autopartco.backend.core.audit.internal.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}