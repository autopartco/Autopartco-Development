package com.autopartco.backend.shared.location;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.autopartco.backend.shared.location.Location;

public interface LocationRepository extends JpaRepository<Location, UUID> {
}