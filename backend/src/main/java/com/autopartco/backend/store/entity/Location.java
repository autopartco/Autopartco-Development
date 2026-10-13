package com.autopartco.backend.store.entity;

import java.math.BigDecimal;
import java.util.Map;

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
@Table(name = "locations")
@SQLDelete(sql = "UPDATE locations SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Location extends BaseEntity {

    @Column(name = "address_line", nullable = false, length = 255)
    private String addressLine;

    @Column(name = "address_complement", length = 255)
    private String addressComplement;

    @Column(nullable = false, length = 150)
    private String city;

    @Column(nullable = false, length = 150)
    private String department;

    @Column(nullable = false, length = 100)
    private String country;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "place_id", length = 255)
    private String placeId;

    @Column(nullable = false, length = 50)
    private String provider;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata;
}