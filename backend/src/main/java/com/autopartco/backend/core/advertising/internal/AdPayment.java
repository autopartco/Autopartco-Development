package com.autopartco.backend.core.advertising.internal;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fila de {@code ad_payments}: cobro de una campaña ({@code ADS-…}). Sigue a {@code seller_payments}: monto en
 * centavos, estado crudo de Wompi ({@code PENDING|APPROVED|DECLINED|ERROR|VOIDED}) o {@code FAILED} (pago pendiente
 * reemplazado por un checkout nuevo), proveedor y entorno. Un solo {@code PENDING} por campaña
 * ({@code uq_adp_pending_campaign}).
 */
@Entity
@Table(name = "ad_payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdPayment {
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String FAILED = "FAILED";
    public static final String WOMPI = "WOMPI";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "campaign_id", nullable = false, updatable = false)
    private UUID campaignId;

    @Column(nullable = false, length = 100, updatable = false)
    private String reference;

    @Column(name = "amount_in_cents", nullable = false, updatable = false)
    private long amountInCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_transaction_id", length = 120)
    private String providerTransactionId;

    @Column(nullable = false, length = 10)
    private String environment;

    @Column(name = "requested_by_account_id")
    private UUID requestedByAccountId;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
