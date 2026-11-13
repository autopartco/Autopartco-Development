package com.autopartco.backend.core.audit.internal;

import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.autopartco.backend.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Registro de auditoría. La tabla solo admite INSERT (trigger {@code forbid_update_delete}): {@link Immutable} evita que
 * Hibernate emita UPDATE cuando el chequeo de cambios ve distinto el JSON de {@code metadata} (p. ej. un {@code Long}
 * que vuelve como {@code Integer} en la copia).
 */
@Entity
@Immutable
@Table(name = "audit_logs")
@SQLDelete(sql = "UPDATE audit_logs SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@SuperBuilder
public class AuditLog extends BaseEntity {
    @Column(name = "account_id")
    private UUID accountId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}