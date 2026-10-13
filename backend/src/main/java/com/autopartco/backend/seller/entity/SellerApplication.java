package com.autopartco.backend.seller.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.autopartco.backend.auth.entity.Account;
import com.autopartco.backend.shared.persistence.BaseEntity;
import com.autopartco.backend.store.entity.Store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "seller_applications")
@SQLDelete(sql = "UPDATE seller_applications SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SellerApplication extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_account_id", nullable = false)
    private Account applicant;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false, unique = true)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SellerPlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_plan_id", nullable = false)
    private SellerPlan selectedPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "legal_type", nullable = false, length = 30)
    private SellerLegalType legalType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SellerApplicationStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private Account reviewedBy;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "onboarding_token_hash", length = 64, unique = true)
    private String onboardingTokenHash;

    @Column(name = "onboarding_token_expires_at")
    private LocalDateTime onboardingTokenExpiresAt;

    @Column(name = "configuration_completed_at")
    private LocalDateTime configurationCompletedAt;

    @Column(name = "trial_starts_at")
    private LocalDateTime trialStartsAt;

    @Column(name = "trial_ends_at")
    private LocalDateTime trialEndsAt;

    @Column(name = "paid_through")
    private LocalDateTime paidThrough;
}
