package com.autopartco.backend.core.catalog.internal;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.autopartco.backend.shared.persistence.BaseEntity;
import com.autopartco.backend.shared.persistence.State;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "products")
@SQLDelete(sql = "UPDATE products SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Product extends BaseEntity {

    /** Almacén dueño (FK a {@code store}, dominio organization; R1: solo el ID). */
    @Column(name = "store_id")
    private UUID storeId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /** Opcional. Único por almacén entre productos vigentes (índice parcial en BD y validación en ProductCatalog). */
    @Column(length = 100)
    private String sku;

    @Column(name = "part_number", length = 50)
    private String partNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manufacturer_id", nullable = false)
    private Manufacturer manufacturer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "state_id", nullable = false)
    private State state;

    /**
     * IDs de {@code compatibilities} (dominio vehicle; R1). Es la tabla de unión tal cual: puede contener IDs de
     * compatibilidades borradas lógicamente, que {@code ListingQueries} filtra con {@code CompatibilityService}.
     */
    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_compatibilities", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "compatibility_id")
    private Set<UUID> compatibilityIds = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private List<ProductImage> images = new ArrayList<>();
}