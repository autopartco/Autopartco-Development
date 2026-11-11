package com.autopartco.backend.core.advertising.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import com.autopartco.backend.core.advertising.AdPlacement;

public interface AdPlacementPriceRepository extends JpaRepository<AdPlacementPrice, AdPlacement> {
}
