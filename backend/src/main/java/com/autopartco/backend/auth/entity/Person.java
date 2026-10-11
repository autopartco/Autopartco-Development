package com.autopartco.backend.auth.entity;

import java.time.LocalDate;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.autopartco.backend.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "persons")
@SQLDelete(sql = "UPDATE persons SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Person extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(nullable = false, length = 100)
    private String surname;

    @Column(nullable = false, unique = true, length = 20)
    private String identification;

    @Column(name = "date_birth", nullable = false)
    private LocalDate dateBirth;

    @Column(name = "number_phone", length = 20)
    private String numberPhone;

}
