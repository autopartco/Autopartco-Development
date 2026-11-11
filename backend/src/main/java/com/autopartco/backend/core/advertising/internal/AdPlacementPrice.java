package com.autopartco.backend.core.advertising.internal;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.autopartco.backend.core.advertising.AdPlacement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Fila de {@code ad_placement_prices}: tarifa diaria en COP (IVA incluido) de una ubicación. */
@Entity
@Table(name = "ad_placement_prices")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdPlacementPrice {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AdPlacement placement;

    @Column(name = "daily_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal dailyPrice;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
