package com.autopartco.backend.core.organization.internal;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.autopartco.backend.shared.persistence.BaseEntity;
import com.autopartco.backend.shared.persistence.State;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "store")
@SQLDelete(sql = "UPDATE store SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Store extends BaseEntity {

    @Column(name = "business_name", nullable = false, unique = true, length = 255)
    private String businessName;

    @Column(nullable = false, unique = true, length = 20)
    private String nit;

    @Column(name = "business_email", nullable = false, unique = true, length = 200)
    private String businessEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "state", nullable = false)
    private State state;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> configuration;

    @Column(name = "logo_path", length = 500)
    private String logoPath;

    @Column(name = "banner_path", length = 500)
    private String bannerPath;
    
    @Builder.Default
    @OneToMany(mappedBy = "store")
    private List<Branch> branches = new ArrayList<>();
}
