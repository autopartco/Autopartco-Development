package com.autopartco.backend.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.autopartco.backend.common.State;

public interface StateRepository extends JpaRepository<State, UUID> {
    Optional<State> findByCode(String code);
}
