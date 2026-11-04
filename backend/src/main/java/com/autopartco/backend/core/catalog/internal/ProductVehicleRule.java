package com.autopartco.backend.core.catalog.internal;

import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.autopartco.backend.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Regla de compatibilidad de un producto: Marca -> Modelo -> Versión, donde {@code modelId}/{@code versionId} nulos
 * significan «todos». IDs planos del dominio vehicle (R1). La coherencia (modelo de la marca, versión del modelo) la
 * valida el servicio y la garantizan las FK compuestas de {@code product_vehicle_rules}.
 */
@Entity
@Table(name = "product_vehicle_rules")
@SQLDelete(sql = "UPDATE product_vehicle_rules SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductVehicleRule extends BaseEntity {

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "make_id", nullable = false, updatable = false)
    private UUID makeId;

    @Column(name = "model_id", updatable = false)
    private UUID modelId;

    @Column(name = "version_id", updatable = false)
    private UUID versionId;
}
