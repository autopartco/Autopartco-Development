package com.autopartco.backend.auth.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.autopartco.backend.common.AuditLogs;

public interface AuditLogsRepository extends JpaRepository<AuditLogs, UUID> {
}
