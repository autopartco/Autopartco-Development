package com.autopartco.backend.auth.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.autopartco.backend.auth.entity.Person;

public interface PersonRepository extends JpaRepository<Person, UUID> {
    boolean existsByIdentification(String identification);

    boolean existsByIdentificationAndIdNot(String identification, UUID id);
}
