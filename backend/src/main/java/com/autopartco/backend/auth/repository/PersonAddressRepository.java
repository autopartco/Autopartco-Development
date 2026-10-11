package com.autopartco.backend.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.autopartco.backend.auth.entity.PersonAddress;

public interface PersonAddressRepository extends JpaRepository<PersonAddress, UUID> {
    List<PersonAddress> findByPerson_IdOrderByIsDefaultDescCreatedAtDesc(UUID personId);

    Optional<PersonAddress> findByIdAndPerson_Id(UUID id, UUID personId);

    Optional<PersonAddress> findFirstByPerson_IdAndIsDefaultTrueOrderByCreatedAtDesc(UUID personId);

    Optional<PersonAddress> findFirstByPerson_IdOrderByCreatedAtDesc(UUID personId);

    Optional<PersonAddress> findFirstByPerson_IdAndIdNotOrderByCreatedAtDesc(UUID personId, UUID id);

    @Modifying
    @Query("UPDATE PersonAddress address SET address.isDefault = false "
            + "WHERE address.person.id = :personId AND address.isDefault = true")
    int clearDefaultForPerson(@Param("personId") UUID personId);
}
