package com.autopartco.backend.store.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.autopartco.backend.store.entity.Location;

public interface LocationRepository extends JpaRepository<Location, UUID> {
}