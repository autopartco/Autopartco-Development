package com.autopartco.backend.core.billing.internal;

import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.autopartco.backend.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "seller_plans")
@SQLDelete(sql = "UPDATE seller_plans SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Plan extends BaseEntity {
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "monthly_price_cop", nullable = false)
    private long monthlyPriceCop;

    @Column(nullable = false, length = 3)
    private String currency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> features;

    @Column(name = "max_branches")
    private Integer maxBranches;

    @Column(name = "max_products")
    private Integer maxProducts;

    @Column(name = "max_users")
    private Integer maxUsers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> capabilities;

    @Column(nullable = false)
    private boolean recommended;

    @Column(nullable = false)
    private boolean enabled;
}
