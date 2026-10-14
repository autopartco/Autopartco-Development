package com.autopartco.backend.core.identity.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;


public interface PersonRepository extends JpaRepository<Person, UUID> {
    boolean existsByIdentification(String identification);

    boolean existsByIdentificationAndIdNot(String identification, UUID id);
}
