package com.autopartco.backend.core.billing.internal;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.SQLRestriction;

import com.autopartco.backend.core.organization.StoreApplicationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Estado de la suscripción de un almacén, sobre la misma tabla {@code seller_applications} que
 * {@code organization.internal.StoreApplication} (diseño §4.4). Las columnas que escribe son disjuntas de las de
 * {@code StoreApplication}: {@code plan_id}, {@code trial_starts_at}, {@code trial_ends_at} y {@code paid_through}.
 * El resto se lee en solo lectura.
 *
 * <p>No extiende {@code BaseEntity} ni tiene {@code @Version}: la única versión de la fila la incrementa
 * {@code StoreApplication}, así que ambas pueden modificarse en la misma transacción sin conflicto. La concurrencia
 * se controla con bloqueo pesimista ({@link StoreSubscriptionRepository#lockById}). La fila la crea siempre
 * {@code StoreApplication}; esta entidad nunca se inserta.</p>
 */
@Entity
@Table(name = "seller_applications")
@SQLRestriction("deleted_at IS NULL")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class StoreSubscription {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "store_id", insertable = false, updatable = false)
    private UUID storeId;

    @Column(name = "applicant_account_id", insertable = false, updatable = false)
    private UUID applicantAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", insertable = false, updatable = false)
    private StoreApplicationStatus status;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", insertable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_plan_id", insertable = false, updatable = false)
    private Plan selectedPlan;

    @Setter
    @Column(name = "trial_starts_at", insertable = false)
    private LocalDateTime trialStartsAt;

    @Setter
    @Column(name = "trial_ends_at", insertable = false)
    private LocalDateTime trialEndsAt;

    @Setter
    @Column(name = "paid_through", insertable = false)
    private LocalDateTime paidThrough;

    @PrePersist
    void rejectInsert() {
        throw new IllegalStateException("StoreSubscription rows are created by organization.StoreApplication.");
    }
}
