package com.autopartco.backend.core.advertising.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.autopartco.backend.core.advertising.AdCampaignStatus;
import com.autopartco.backend.core.advertising.AdPlacement;
import com.autopartco.backend.core.advertising.AdTargeting;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
 * Fila de {@code ad_campaigns} con sus productos ({@code ad_campaign_products}). Sin borrado lógico: solo una campaña
 * en {@code DRAFT} se borra, y de verdad. El precio ({@code total_price}) lo fija siempre el servidor.
 */
@Entity
@Table(name = "ad_campaigns")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdCampaign {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "store_id", nullable = false, updatable = false)
    private UUID storeId;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdCampaignStatus status;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "varchar(20)[]")
    private String[] placements;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> targeting;

    @Column(name = "total_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "rejected_reason", length = 300)
    private String rejectedReason;

    @Builder.Default
    @Column(name = "requires_manual_refund", nullable = false)
    private boolean requiresManualRefund = false;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ad_campaign_products", joinColumns = @JoinColumn(name = "campaign_id"))
    @Column(name = "product_id", nullable = false)
    private Set<UUID> productIds = new LinkedHashSet<>();

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

    /** Ubicaciones como enum (los valores desconocidos se ignoran). */
    public List<AdPlacement> placementList() {
        if (placements == null) {
            return List.of();
        }
        return Arrays.stream(placements).map(AdPlacement::parse).flatMap(java.util.Optional::stream).toList();
    }

    public void setPlacementList(List<AdPlacement> values) {
        placements = values.stream().map(AdPlacement::name).toArray(String[]::new);
    }

    public AdTargeting targetingValue() {
        return AdTargeting.fromJson(targeting);
    }
}
