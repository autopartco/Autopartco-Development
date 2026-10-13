package com.autopartco.backend.shared.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;


public interface StateRepository extends JpaRepository<State, UUID> {
    Optional<State> findByCode(String code);
}
