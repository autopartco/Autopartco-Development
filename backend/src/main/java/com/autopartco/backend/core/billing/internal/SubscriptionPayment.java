package com.autopartco.backend.core.billing.internal;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.autopartco.backend.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "seller_payments")
@SQLDelete(sql = "UPDATE seller_payments SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SubscriptionPayment extends BaseEntity {
    // R1: la solicitud de almacén (organization) se referencia por ID, en la misma columna application_id.
    @Column(name = "application_id", nullable = false)
    private UUID storeApplicationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(nullable = false, unique = true, length = 100)
    private String reference;

    @Column(name = "amount_in_cents", nullable = false)
    private long amountInCents;

    @Column(name = "prepaid_months", nullable = false)
    private int prepaidMonths;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "provider_transaction_id", unique = true, length = 120)
    private String providerTransactionId;

    // Obligatorio en el INSERT: la columna no tiene valor por defecto en el entity.
    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "period_starts_at")
    private LocalDateTime periodStartsAt;

    @Column(name = "period_ends_at")
    private LocalDateTime periodEndsAt;

    // Copia del plan al momento del pago: los cambios de precio no afectan periodos ya pagados.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "plan_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> planSnapshot;

    @Column(name = "requested_by_account_id")
    private UUID requestedByAccountId;
}
