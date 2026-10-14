package com.autopartco.backend.core.pricing.internal;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.autopartco.backend.core.pricing.PromotionAttributeOperator;
import com.autopartco.backend.core.pricing.PromotionDiscountType;
import com.autopartco.backend.core.pricing.PromotionScope;
import com.autopartco.backend.core.pricing.PromotionTargetType;
import com.autopartco.backend.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "promotions")
@SQLDelete(sql = "UPDATE promotions SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Promotion extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromotionScope scope;

    /** Almacén dueño en promociones {@code STORE}; nulo en las de plataforma (dominio organization; R1). */
    @Column(name = "store_id")
    private UUID storeId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private PromotionTargetType targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "attribute_key", length = 100)
    private String attributeKey;

    @Column(name = "attribute_value", length = 255)
    private String attributeValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "attribute_operator", length = 20)
    private PromotionAttributeOperator attributeOperator;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private PromotionDiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;
}
