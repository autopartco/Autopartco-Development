package com.autopartco.backend.core.vehicle.internal;

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

@Entity
@Table(name = "compatibilities")
@SQLDelete(sql = "UPDATE compatibilities SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Compatibility extends BaseEntity {

    @Column(name = "make_id", nullable = false)
    private UUID makeId;

    @Column(name = "model_id", nullable = false)
    private UUID modelId;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;
}