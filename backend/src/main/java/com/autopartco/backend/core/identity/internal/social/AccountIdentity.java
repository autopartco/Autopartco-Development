package com.autopartco.backend.core.identity.internal.social;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.autopartco.backend.core.identity.internal.Account;
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

/** Vínculo de una cuenta con su identidad en un proveedor social ({@code provider} + {@code subject} únicos). */
@Entity
@Table(name = "account_identities")
@SQLDelete(sql = "UPDATE account_identities SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AccountIdentity extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(length = 255)
    private String email;
}
