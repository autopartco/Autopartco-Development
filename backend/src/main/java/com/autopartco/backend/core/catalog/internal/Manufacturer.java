package com.autopartco.backend.core.catalog.internal;

import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.autopartco.backend.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "manufacturers")
@SQLDelete(sql = "UPDATE manufacturers SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Manufacturer extends BaseEntity {

    /** La unicidad la garantiza el índice parcial sobre {@code immutable_unaccent(lower(name))}. */
    @Column(nullable = false, length = 100)
    private String name;

    /** {@code false} = sugerido por un almacén y pendiente de moderación. */
    @Column(nullable = false)
    @Builder.Default
    private boolean approved = true;

    @Column(name = "suggested_by_store_id")
    private UUID suggestedByStoreId;
}