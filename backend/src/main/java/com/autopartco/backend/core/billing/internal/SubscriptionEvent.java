package com.autopartco.backend.core.billing.internal;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Evento de solo inserción de la suscripción (tabla {@code seller_subscription_events}, SA-D1). */
@Entity
@Table(name = "seller_subscription_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SubscriptionEvent {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "store_id", nullable = false, updatable = false)
    private UUID storeId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 30)
    private String eventType;

    @Column(name = "plan_id", updatable = false)
    private UUID planId;

    @Column(name = "previous_plan_id", updatable = false)
    private UUID previousPlanId;

    @Column(name = "payment_id", updatable = false)
    private UUID paymentId;

    @Column(name = "actor_account_id", updatable = false)
    private UUID actorAccountId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    void assignDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (details == null) {
            details = Map.of();
        }
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
    }
}
