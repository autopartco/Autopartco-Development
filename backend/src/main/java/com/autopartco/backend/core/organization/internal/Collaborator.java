package com.autopartco.backend.core.organization.internal;

import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.autopartco.backend.shared.persistence.BaseEntity;
import com.autopartco.backend.shared.persistence.State;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "collaborators")
@SQLDelete(sql = "UPDATE collaborators SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Collaborator extends BaseEntity {

    // R1: persona de identity por ID.
    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collab_store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "state", nullable = false)
    private State state;

    @ManyToMany(cascade = CascadeType.PERSIST)
    @JoinTable(
            name = "roles_collaborators",
            joinColumns = @JoinColumn(name = "collaborator_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<StoreRole> roles;
}