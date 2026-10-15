package com.autopartco.backend.core.identity.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface PersonAddressRepository extends JpaRepository<PersonAddress, UUID> {
    @Query("""
            SELECT address FROM PersonAddress address
            WHERE address.person.id = :personId
            ORDER BY address.isDefault DESC, address.createdAt DESC
            """)
    List<PersonAddress> findAllOfPerson(@Param("personId") UUID personId);

    Optional<PersonAddress> findByIdAndPerson_Id(UUID id, UUID personId);

    @Query("""
            SELECT address FROM PersonAddress address
            WHERE address.person.id = :personId
              AND address.isDefault = true
            ORDER BY address.createdAt DESC
            LIMIT 1
            """)
    Optional<PersonAddress> findDefaultOf(@Param("personId") UUID personId);

    @Query("""
            SELECT address FROM PersonAddress address
            WHERE address.person.id = :personId
            ORDER BY address.createdAt DESC
            LIMIT 1
            """)
    Optional<PersonAddress> findLatestOf(@Param("personId") UUID personId);

    @Query("""
            SELECT address FROM PersonAddress address
            WHERE address.person.id = :personId
              AND address.id <> :addressId
            ORDER BY address.createdAt DESC
            LIMIT 1
            """)
    Optional<PersonAddress> findLatestOtherThan(
            @Param("personId") UUID personId,
            @Param("addressId") UUID addressId
    );

    @Modifying
    @Query("UPDATE PersonAddress address SET address.isDefault = false "
            + "WHERE address.person.id = :personId AND address.isDefault = true")
    int clearDefaultForPerson(@Param("personId") UUID personId);
}
